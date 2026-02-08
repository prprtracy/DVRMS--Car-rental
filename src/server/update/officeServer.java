package server.update;

import common.DVRMS;
import common.IdCheck;
import server.model.Reservation;
import server.model.vehicleRecord;
import server.model.WaitingList;
import common.Logger;



import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class officeServer extends UnicastRemoteObject implements DVRMS {
    private final String office; //MTL, WPG, BNF

    private final ConcurrentHashMap<String, vehicleRecord> vehicles = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Double> budgets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> remoteOfficeReservations = new ConcurrentHashMap<>();


    private static final double defaultBudget = 1000.0; //set the default budget as 1000$

    private void logServer(String op, String params, String result) {
        Logger.log("logs/server_" + office + ".txt",
                op + " | " + params + " | result=" + result);
    }

    private void ensureBudget(String customerID) {
        if (!budgets.containsKey(customerID)) {
            budgets.put(customerID, defaultBudget);
        }
    }

    private boolean isLocalUser(String id) {
        return office.equals(IdCheck.officeLocation(id));
    }

    private void startUdpListener() {
        Thread t = new Thread(() -> {
            try (java.net.DatagramSocket socket = new java.net.DatagramSocket(udpPort())) {
                byte[] buf = new byte[4096];
                while (true) {
                    java.net.DatagramPacket req = new java.net.DatagramPacket(buf, buf.length);
                    socket.receive(req);

                    String msg = new String(req.getData(), 0, req.getLength()).trim();
                    String reply = handleUdpRequest(msg);

                    byte[] out = reply.getBytes();
                    java.net.DatagramPacket resp = new java.net.DatagramPacket(
                            out, out.length, req.getAddress(), req.getPort()
                    );
                    socket.send(resp);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        t.setDaemon(true);
        t.start();
    }

    // count how many existing reservations overlap with the given date range
    private int countOverlapping(vehicleRecord v, String startDate, String endDate) {
        int count = 0;
        for (Reservation r : v.reservations) {
            if (r.overlaps(startDate, endDate)) count++;
        }
        return count;
    }

    // add to the waitlist first queue (FIFO)
    private int tryAssignFromWaitlist(vehicleRecord v) {
        int assigned = 0;

        while (!v.waitlist.isEmpty()) {
            WaitingList w = v.waitlist.peekFirst();

            // 1) check capacity for the waiting request
            if (v.vehicleNumber <= 0) break;
            int overlapCount = countOverlapping(v, w.startDate(), w.endDate());
            if (overlapCount >= v.vehicleNumber) break; // FIFO: head cannot be satisfied now

            // 2) check budget
            ensureBudget(w.customerID());
            double remaining = budgets.get(w.customerID());
            if (remaining < v.price) {
                v.waitlist.removeFirst(); // drop this request to avoid blocking queue forever
                continue;
            }

            // 3) assign it
            budgets.put(w.customerID(), remaining - v.price);
            v.reservations.add(new Reservation(w.customerID(), w.startDate(), w.endDate()));
            v.waitlist.removeFirst();
            assigned++;
        }

        return assigned;
    }

    private String handleUdpRequest(String msg) {
        try {
            String[] p = msg.split("\\|");
            String op = p[0];

            if ("FIND".equals(op)) {
                return findVehicleLocalOnly(p[1]); // only search local office
            }

            if ("RESERVE".equals(op)) {
                boolean wantWaitlist = true;
                if (p.length >= 6) {
                    wantWaitlist = Boolean.parseBoolean(p[5]);
                }
                return reserveVehicleLocalOnly(p[1], p[2], p[3], p[4], wantWaitlist); // reservation in the local office only
            }

            if ("CANCEL".equals(op)) {
                return cancelReservationLocalOnly(p[1], p[2]);
            }

            return "ERR|UnknownOp";
        } catch (Exception e) {
            return "ERR|" + e.getMessage();
        }
    }

    private String udpRequest(String targetOffice, String msg) throws Exception {
        int port = portOfOffice(targetOffice);
        try (java.net.DatagramSocket socket = new java.net.DatagramSocket()) {
            byte[] data = msg.getBytes();
            java.net.DatagramPacket req = new java.net.DatagramPacket(
                    data, data.length, java.net.InetAddress.getByName("localhost"), port
            );
            socket.send(req);

            byte[] buf = new byte[4096];
            java.net.DatagramPacket resp = new java.net.DatagramPacket(buf, buf.length);
            socket.receive(resp);

            return new String(resp.getData(), 0, resp.getLength()).trim();
        }
    }

    private String findVehicleLocalOnly(String vehicleType) {
        StringBuilder sb = new StringBuilder();
        for (var v : vehicles.values()) {
            if (!v.vehicleType.equalsIgnoreCase(vehicleType)) continue;

            // Availability status based on inventory quantity.
            String status = (v.vehicleNumber > 0) ? "Available" : "Reserved";
            sb.append(v.vehicleID).append(" ")
                    .append(v.vehicleType).append(" ")
                    .append(status).append(" ")
                    .append((double) v.price).append(" ")
                    .append(office)
                    .append("\n");
        }
        return sb.length() == 0 ? "" : sb.toString();
    }

    private String cancelReservationLocalOnly(String customerID, String vehicleID) throws RemoteException {
        vehicleRecord v = vehicles.get(vehicleID);
        if (v == null) return "FAIL|No reservation found for " + vehicleID;

        // Lock vehicle record to prevent concurrent reserve/update/cancel
        v.lock.lock();
        try {
            Reservation target = null;
            for (Reservation r : v.reservations) {
                if (r.customerID().equals(customerID)) {
                    target = r;
                    break;
                }
            }
            if (target == null) {
                return "FAIL|Sorry there is no reservation found for customer ID: " + customerID
                        + " on vehicle " + vehicleID + ".";
            }
            v.reservations.remove(target);

            ensureBudget(customerID);
            budgets.put(customerID, budgets.get(customerID) + v.price);

            int assigned = tryAssignFromWaitlist(v);

            String res = "SUCCESS|Reservation cancelled for " + vehicleID + " at office " + office + " by " + customerID
                    + ". Auto-assigned from waitlist: " + assigned;

            logServer("cancleReservation",
                    "customerID=" + customerID + ", vehicleID=" + vehicleID, res);

            return res;
        } finally {
            v.lock.unlock();
        }
    }

    private String reserveVehicleLocalOnly(String customerID, String vehicleID, String startDate, String endDate, boolean wantWaitlist) throws RemoteException {

        ensureBudget(customerID);

        vehicleRecord v = vehicles.get(vehicleID);
        if (v == null) return "No such vehicle with ID: " + vehicleID;

        if (!Reservation.isValidRange(startDate, endDate)) {
            return "Invalid date range.";
        }

        // Lock vehicle record to prevent concurrent reserve/update/cancel
        v.lock.lock();

        try {
            if (v.vehicleNumber <= 0) {
                return "FAIL|Vehicle quantity is 0.";
            }

            int overlapCount = countOverlapping(v, startDate, endDate);
            if (overlapCount >= v.vehicleNumber) {
                if (wantWaitlist) {
                    // adding to waiting list (FIFO)
                    v.waitlist.addLast(new WaitingList(customerID, startDate, endDate));
                    return "FAIL|Vehicle not available for the requested period. Added to waitlist.";
                }
                return "FAIL|Vehicle not available for the requested period.";
            }


            double remaining = budgets.get(customerID);
            if (remaining < v.price) {
                return "FAIL|Not enough budget.";
            }

            budgets.put(customerID, remaining - v.price);
            v.reservations.add(new Reservation(customerID, startDate, endDate));

            String res = "SUCCESS|Reservation successful for " + vehicleID
                    + " at office " + office
                    + " By the Customer " + customerID;

            logServer("reserveVehicle",
                    "customerID=" + customerID + ", vehicleID=" + vehicleID + ", start=" + startDate + ", end=" + endDate,
                    res);

            return res;
        } finally {
            v.lock.unlock();
        }
    }

    /* ============================ Add UDP port ============================ */
    private int udpPort() {
        return switch (office) {
            case "MTL" -> 6000;
            case "WPG" -> 6001;
            case "BNF" -> 6002;
            default -> throw new IllegalStateException("Unknown office: " + office);
        };
    }

    private int portOfOffice(String off) {
        return switch (off) {
            case "MTL" -> 6000;
            case "WPG" -> 6001;
            case "BNF" -> 6002;
            default -> throw new IllegalArgumentException("Unknown office: " + off);
        };
    }

    public officeServer(String office) throws RemoteException {
        super();
        this.office = office;

        startUdpListener();
    }

    /* ============================ Manager Role ============================ */

    @Override
    public String addVehicle(String managerID, int vehicleNumber, String vehicleType, String vehicleID, double reservationPrice) throws RemoteException {
        //check manager and the office location
        if (!IdCheck.isManager(managerID)) return "Wrong manager ID";
        if (!isLocalUser(managerID)) return "Wrong office of manager";

        vehicleRecord v = vehicles.get(vehicleID);

        if (v == null) {
            int q = Math.max(0, vehicleNumber);
            vehicles.put(vehicleID, new vehicleRecord(vehicleID, vehicleType, q, reservationPrice));
        } else {
            v.vehicleType = vehicleType;
            v.vehicleNumber = Math.max(0, v.vehicleNumber + vehicleNumber);
            v.price = reservationPrice;
        }
        // try assign from waitlist (vehicle becomes available)
        vehicleRecord rec = vehicles.get(vehicleID);
        int assigned = 0;

        rec.lock.lock();
        try {
            assigned = tryAssignFromWaitlist(rec);
        } finally {
            rec.lock.unlock();
        }

        String res = "The vehicle quantity: " + vehicleNumber
                + ", vehicle ID: " + vehicleID
                + ", vehicle type: " + vehicleType
                + ", reservation price: $" + reservationPrice
                + "\nhas been successfully added to the office " + office
                + " by the manager " + managerID + "."
                + "\nAuto-assigned from waitlist: " + assigned;

        logServer("addVehicle",
                "managerID=" + managerID + ", vehicleID=" + vehicleID + ", type=" + vehicleType +
                        ", quantity=" + vehicleNumber + ", price=" + reservationPrice, res);

        return res;
    }

    @Override
    public String removeVehicle(String managerID, String vehicleID) throws RemoteException {
        //check manager and the office location
        if (!IdCheck.isManager(managerID)) return "Wrong manager ID";
        if (!isLocalUser(managerID)) return "Wrong office of manager";

        vehicleRecord v = vehicles.get(vehicleID);
        if (v == null) return "Sorry, the vehicle " + vehicleID + " does not exist in the office " + office + ". ";

        // Lock vehicle record to prevent concurrent reserve/update/cancel
        v.lock.lock();

        try {
            vehicleRecord removed = vehicles.remove(vehicleID);
            if (removed == null) return "Vehicle " + vehicleID + " already removed.";

            int refundedCount = 0;
            for (Reservation r : removed.reservations) {
                ensureBudget(r.customerID());
                double old = budgets.get(r.customerID());
                budgets.put(r.customerID(), old + removed.price);
                refundedCount++;
            }
            removed.reservations.clear();

            int waitCount = removed.waitlist.size();
            removed.waitlist.clear();

            String res = "Vehicle " + vehicleID + " removed at office " + office +
                    " by manager " + managerID +
                    ". Refunded reservations: " + refundedCount +
                    ", Removed waitlist requests: " + waitCount;

            logServer("removeVehicle",
                    "managerID=" + managerID + ", vehicleID=" + vehicleID,
                    res);

            return res;

        } finally {
            v.lock.unlock();
        }
    }

    @Override
    public String listAvailableVehicle(String managerID) throws RemoteException {
        //check manager and the office location
        if (!IdCheck.isManager(managerID)) return "Wrong manager ID";
        if (!isLocalUser(managerID)) return "Wrong office of manager";

        StringBuilder sb = new StringBuilder();
        for (var vr : vehicles.values()) {
            sb.append(vr.vehicleID).append(" ")
                    .append(vr.vehicleType).append(" ")
                    .append(vr.vehicleNumber).append(" ")
                    .append((double) vr.price).append("\n");
        }

        return sb.length() == 0 ? "There is no vehicles. " : sb.toString();
    }

    /* ============================ User Role ============================ */
    @Override
    public String getBudget(String customerID) throws RemoteException {
        String result;

        if (!IdCheck.isCustomer(customerID)) {
            result = "FAIL|Wrong customer ID";
        } else if (!isLocalUser(customerID)) {
            result = "FAIL|Wrong office of customer";
        } else {
            ensureBudget(customerID);
            result = "SUCCESS|Budget=" + budgets.get(customerID);
        }

        logServer("getBudget", "customerID=" + customerID, result);
        return result;
    }

    @Override
    public String reserveVehicle(String customerID, String vehicleID, String startDate, String endDate, boolean wantWaitlist) throws RemoteException {

        String result;

        //check customer and the office location
        if (!IdCheck.isCustomer(customerID)) {
            result = "Wrong customer ID";
        } else if (!isLocalUser(customerID)) {
            result = "Wrong office of customer";
        } else {
            String targetOffice = vehicleID.substring(0, 3).toUpperCase();

            if (targetOffice.equals(office)) {
                // local reservation
                result = reserveVehicleLocalOnly(customerID, vehicleID, startDate, endDate, wantWaitlist);
            } else {
                // remote office limit: only 1 vehicle per remote office
                remoteOfficeReservations.putIfAbsent(customerID, ConcurrentHashMap.newKeySet());

                if (remoteOfficeReservations.get(customerID).contains(targetOffice)) {
                    result = "FAIL|You can only reserve one vehicle from office " + targetOffice;
                } else {
                    try {
                        String resp = udpRequest(
                                targetOffice,
                                "RESERVE|" + customerID + "|" + vehicleID + "|" + startDate + "|" + endDate + "|" + wantWaitlist
                        );

                        // only mark success when protocol matches
                        if (resp != null && resp.startsWith("SUCCESS|")) {
                            remoteOfficeReservations.get(customerID).add(targetOffice);
                        }

                        result = resp;
                    } catch (Exception e) {
                        result = "ERR|UDP RESERVE error: " + e.getMessage();
                    }
                }
            }
        }

        logServer("reserveVehicle",
                "customerID=" + customerID + ", vehicleID=" + vehicleID +
                        ", start=" + startDate + ", end=" + endDate,
                result);

        return result;

    }

    @Override
    public String updateReservation(String customerID, String vehicleID, String startDate, String endDate) throws RemoteException {
        //check customer and the office location
        if (!IdCheck.isCustomer(customerID)) return "\nWrong customer ID";
        if (!isLocalUser(customerID)) return "\nWrong office of customer";

        //check date validation
        if (!Reservation.isValidRange(startDate, endDate)) {
            return "\nInvalid date. Please use ddmmyyyy and ensure endDate >= startDate.";
        }

        vehicleRecord v = vehicles.get(vehicleID);
        if (v == null) return "Sorry there is no such vehicle with ID: " + vehicleID;

        // Lock vehicle record to prevent concurrent reserve/update/cancel
        v.lock.lock();

        try {

            Reservation target = null;

            //check if the customer has reservation
            for (Reservation r : v.reservations) {
                if (r.customerID().equals(customerID)) {
                    target = r;
                    break;
                }
            }
            if (target == null) return "Sorry, there is no reservation found with customer ID: " + customerID;

            //check the date conflict.
            for (Reservation r : v.reservations) {
                if (r == target) continue;
                if (r.overlaps(startDate, endDate)) return "Sorry the date requested is not avalible.";
            }

            v.reservations.remove(target);
            v.reservations.add(new Reservation(customerID, startDate, endDate));

            return "\nThe reservtion from " + startDate
                    + " to " + endDate
                    + " for the vehicle ID: " + vehicleID
                    + " has been successfully updated from the office " + office
                    + " by the customer " + customerID + ". ";

        } finally {
            v.lock.unlock();
        }

    }

    @Override
    public String cancelReservation(String customerID, String vehicleID) throws RemoteException {

        String result;

        //check customer and the office location
        if (!IdCheck.isCustomer(customerID)) {
            result = "Wrong customerID";
        } else if (!isLocalUser(customerID)) {
            result = "Wrong office of customer";
        } else {
            String targetOffice = vehicleID.substring(0, 3).toUpperCase();

            if (targetOffice.equals(office)) {
                result = cancelReservationLocalOnly(customerID, vehicleID);
            } else {
                try {
                    String resp = udpRequest(targetOffice,
                            "CANCEL|" + customerID + "|" + vehicleID);

                    // release only 1 after cancel，
                    //ONLY release remote constraint on real success
                    if (resp != null && resp.startsWith("SUCCESS|")) {
                        remoteOfficeReservations.computeIfPresent(customerID, (k, set) -> {
                            set.remove(targetOffice);
                            return set;
                        });
                    }
                    result = resp;

                } catch (Exception e) {
                    result = "ERR|UDP CANCEL error: " + e.getMessage();
                }
            }
        }

        // logging
        logServer("cancelReservation",
                "customerID=" + customerID + ", vehicleID=" + vehicleID,
                result);

        return result;

    }

    @Override
    public String findVehicle(String customerID, String vehicleType) throws RemoteException {
        String result;

        // basic validation
        if (!IdCheck.isCustomer(customerID)) {
            result = "\nWrong customer ID";
        } else if (!isLocalUser(customerID)) {
            result = "\nWrong office of customer";
        } else {
            StringBuilder all = new StringBuilder();

            // local office
            all.append(findVehicleLocalOnly(vehicleType));

            // other offices via UDP
            try {
                for (String off : new String[]{"MTL", "WPG", "BNF"}) {
                    if (off.equals(office)) continue;

                    String r = udpRequest(off, "FIND|" + vehicleType);
                    if (r != null && !r.isBlank() && !r.startsWith("ERR|")) {
                        all.append(r);
                    }
                }
                result = (all.length() == 0) ? "\nNo matching vehicles." : "\n" + all;
            } catch (Exception e) {
                result = "\nFIND failed due to UDP error: " + e.getMessage();
            }
        }

        // logging
        logServer("findVehicle",
                "customerID=" + customerID + ", type=" + vehicleType,
                result);

        return result;

    }
}

