package client;

import common.DVRMS;
import common.IdCheck;
import common.Logger;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Locale;
import java.util.Scanner;


public class customerClient {

    private static final String REG_HOST = "localhost";
    private static final int REG_PORT = 1099;

    private static void log(String customerID, String msg) {
        Logger.log("logs/client_" + customerID + ".txt", msg);
    }

    private static String normalizeDate(String input) {
        if (input == null) return "";
        return input.replaceAll("\\D", "");
    }

    private static void printMenu(String customerID, String homeOffice) {
        System.out.println();
        System.out.println("============== DVRMS Customer Menu ==============");
        System.out.println("Logged in as: " + customerID + " (home=" + homeOffice + ")");
        System.out.println("1) Reserve a vehicle");
        System.out.println("2) Update reservation");
        System.out.println("3) Cancel reservation");
        System.out.println("4) Find vehicles by type (across all offices)");
        System.out.println("5) Check my remaining budget");
        System.out.println("9) Switch user (sign out / sign in)");
        System.out.println("0) Exit");
        System.out.println("================================================");
        System.out.print("Choose an option: ");
    }

    private static boolean looksLikeWantWaitlist(String serverResp) {
        if (serverResp == null) return false;
        String s = serverResp.toLowerCase(Locale.ROOT);
        return s.contains("not available") || s.contains("quantity is 0") || s.contains("unavailable")
                || s.contains("overlap") || s.contains("conflict");
    }

    private static void safePrint(String title, String resp) {
        System.out.println();
        System.out.println("---- " + title + " ----");
        System.out.println(resp == null ? "(no response)" : resp);
        System.out.println("------------------------");
    }

    private static void showBudget(Session session) {
        try {
            log(session.customerID, "SEND getBudget");
            String resp = session.stub.getBudget(session.customerID);
            log(session.customerID, "RECV getBudget | " + resp);
            safePrint("Remaining Budget", resp);
        } catch (Exception e) {
            safePrint("Remaining Budget", "FAIL|Could not fetch budget: " + e.getMessage());
        }
    }


    private static class Session {
        String customerID;
        String homeOffice;
        DVRMS stub;
    }

    private static Session login(Scanner sc) throws Exception {
        Session s = new Session();

        while (true) {
            System.out.print("Enter Customer ID (e.g., MTLU1111 / WPGU2222 / BNFU3333): ");
            String customerID = sc.nextLine().trim().toUpperCase(Locale.ROOT);

            if (!IdCheck.isCustomer(customerID)) {
                System.out.println("Invalid customerID: 4th character must be 'U' (e.g., MTLU1111). Try again.");
                continue;
            }
            String office = IdCheck.officeLocation(customerID);
            if (!("MTL".equals(office) || "WPG".equals(office) || "BNF".equals(office))) {
                System.out.println("Invalid office prefix in customerID. Must start with MTL/WPG/BNF. Try again.");
                continue;
            }

            Registry reg = LocateRegistry.getRegistry(REG_HOST, REG_PORT);
            DVRMS stub = (DVRMS) reg.lookup(office);

            s.customerID = customerID;
            s.homeOffice = office;
            s.stub = stub;

            System.out.println("\nConnected. Home office server = " + office);
            log(customerID, "LOGIN homeOffice=" + office);
            break;
        }
        return s;
    }

    public static void main(String[] args) {
        try (Scanner sc = new Scanner(System.in)) {

            Session session = login(sc);

            while (true) {
                printMenu(session.customerID, session.homeOffice);
                String choice = sc.nextLine().trim();

                if ("0".equals(choice)) {
                    log(session.customerID, "EXIT");
                    System.out.println("Bye.");
                    return;
                }

                if ("9".equals(choice)) {
                    log(session.customerID, "LOGOUT");
                    System.out.println("\nSigned out.\n");
                    session = login(sc);
                    // Auto-display budget after switching users
                    showBudget(session);
                    continue;
                }

                switch (choice) {
                    case "1" -> {
                        System.out.print("VehicleID (e.g., MTL1001 / WPG2001 / BNF3001): ");
                        String vehicleID = sc.nextLine().trim().toUpperCase(Locale.ROOT);

                        System.out.print("Start date (ddMMyyyy, e.g., 01022026): ");
                        String start = normalizeDate(sc.nextLine());

                        System.out.print("End date   (ddMMyyyy, e.g., 07022026): ");
                        String end = normalizeDate(sc.nextLine());

                        log(session.customerID, "SEND reserveVehicle vehicleID=" + vehicleID + " start=" + start + " end=" + end + " wantWaitlist=false");
                        String resp = session.stub.reserveVehicle(session.customerID, vehicleID, start, end, false);
                        log(session.customerID, "RECV reserveVehicle | " + resp);
                        safePrint("Reserve Vehicle", resp);

                        if (looksLikeWantWaitlist(resp) && resp != null && resp.startsWith("FAIL|")) {
                            System.out.print("Reservation failed due to availability. Join waiting list? (y/n): ");
                            String yn = sc.nextLine().trim().toLowerCase(Locale.ROOT);
                            if (yn.startsWith("y")) {
                                log(session.customerID, "SEND reserveVehicle (waitlist) vehicleID=" + vehicleID + " start=" + start + " end=" + end + " wantWaitlist=true");
                                String resp2 = session.stub.reserveVehicle(session.customerID, vehicleID, start, end, true);
                                log(session.customerID, "RECV reserveVehicle (waitlist) | " + resp2);
                                safePrint("Reserve Vehicle (Waitlist)", resp2);
                            }
                        }
                    }
                    case "2" -> {
                        System.out.print("VehicleID you reserved (e.g., MTL1001): ");
                        String vehicleID = sc.nextLine().trim().toUpperCase(Locale.ROOT);

                        System.out.print("New start date (ddMMyyyy): ");
                        String start = normalizeDate(sc.nextLine());

                        System.out.print("New end date   (ddMMyyyy): ");
                        String end = normalizeDate(sc.nextLine());

                        log(session.customerID, "SEND updateReservation vehicleID=" + vehicleID + " start=" + start + " end=" + end);
                        String resp = session.stub.updateReservation(session.customerID, vehicleID, start, end);
                        log(session.customerID, "RECV updateReservation | " + resp);
                        safePrint("Update Reservation", resp);
                    }
                    case "3" -> {
                        System.out.print("VehicleID to cancel (e.g., MTL1001): ");
                        String vehicleID = sc.nextLine().trim().toUpperCase(Locale.ROOT);

                        log(session.customerID, "SEND cancelReservation vehicleID=" + vehicleID);
                        String resp = session.stub.cancelReservation(session.customerID, vehicleID);
                        log(session.customerID, "RECV cancelReservation | " + resp);
                        safePrint("Cancel Reservation", resp);
                    }
                    case "4" -> {
                        System.out.print("Vehicle type (e.g., Sedan / SUV / Truck): ");
                        String type = sc.nextLine().trim();

                        log(session.customerID, "SEND findVehicle type=" + type);
                        String resp = session.stub.findVehicle(session.customerID, type);
                        log(session.customerID, "RECV findVehicle | " + resp);
                        safePrint("Find Vehicle", (resp == null || resp.isBlank()) ? "(no matches)" : resp);
                    }
                    case "5" -> {
                        log(session.customerID, "SEND getBudget");
                        String resp = session.stub.getBudget(session.customerID);
                        log(session.customerID, "RECV getBudget | " + resp);
                        safePrint("Remaining Budget", resp);
                    }
                    default -> System.out.println("Unknown option. Please choose 0,1,2,3,4,5,9.");
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
