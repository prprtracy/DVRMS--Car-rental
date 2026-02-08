package client;

import common.DVRMS;
import common.IdCheck;
import common.Logger;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Locale;
import java.util.Scanner;

public class managerClient {

    private static final String REG_HOST = "localhost";
    private static final int REG_PORT = 1099;

    private static void log(String managerID, String msg) {
        Logger.log("logs/client_" + managerID + ".txt", msg);
    }

    private static void safePrint(String title, String resp) {
        System.out.println();
        System.out.println("---- " + title + " ----");
        System.out.println(resp == null ? "(no response)" : resp);
        System.out.println("------------------------");
    }

    private static void printMenu(String managerID, String office) {
        System.out.println();
        System.out.println("============== DVRMS Manager Menu ==============");
        System.out.println("Current manager: " + managerID + " (office=" + office + ")");
        System.out.println("1) Add / Update vehicle");
        System.out.println("2) Remove vehicle");
        System.out.println("3) List available vehicles (this office)");
        System.out.println("9) Switch manager (sign out / sign in)");
        System.out.println("0) Exit");
        System.out.println("================================================");
        System.out.print("Choose an option: ");
    }

    private static DVRMS connectStub(String office) throws Exception {
        Registry reg = LocateRegistry.getRegistry(REG_HOST, REG_PORT);
        return (DVRMS) reg.lookup(office);
    }

    private static String doList(DVRMS stub, String managerID) {
        try {
            log(managerID, "SEND listAvailableVehicle");
            String resp = stub.listAvailableVehicle(managerID);
            log(managerID, "RECV listAvailableVehicle | " + resp);
            safePrint("List Available Vehicles", (resp == null || resp.isBlank()) ? "(none)" : resp);
            return resp;
        } catch (Exception e) {
            String msg = "ERROR listAvailableVehicle: " + e.getMessage();
            log(managerID, msg);
            safePrint("List Available Vehicles", msg);
            return msg;
        }
    }

    private static class Session {
        String managerID;
        String office;
        DVRMS stub;
    }

    private static Session login(Scanner sc) throws Exception {
        Session s = new Session();
        while (true) {
            System.out.print("Enter Manager ID (e.g., MTLM1111 / WPGM2222 / BNFM3333): ");
            String id = sc.nextLine().trim().toUpperCase(Locale.ROOT);

            if (!IdCheck.isManager(id)) {
                System.out.println("Invalid managerID: 4th character must be 'M' (e.g., MTLM1111). Try again.");
                continue;
            }
            String office = IdCheck.officeLocation(id);
            if (!("MTL".equals(office) || "WPG".equals(office) || "BNF".equals(office))) {
                System.out.println("Invalid office prefix. Must start with MTL/WPG/BNF. Try again.");
                continue;
            }

            s.managerID = id;
            s.office = office;
            s.stub = connectStub(office);

            System.out.println("\nConnected. Manager office server = " + office);
            log(id, "LOGIN office=" + office);

            // Optional: show list on login for demo-friendliness
            doList(s.stub, s.managerID);
            return s;
        }
    }

    public static void main(String[] args) {
        try (Scanner sc = new Scanner(System.in)) {

            Session session = login(sc);

            while (true) {
                printMenu(session.managerID, session.office);
                String choice = sc.nextLine().trim();

                if ("0".equals(choice)) {
                    log(session.managerID, "EXIT");
                    System.out.println("Bye.");
                    return;
                }

                switch (choice) {
                    case "1" -> {
                        // addVehicle(managerID, vehicleNumber(quantity), vehicleType, vehicleID, reservationPrice)
                        System.out.print("VehicleID (e.g., MTL1001 / WPG2001 / BNF3001): ");
                        String vehicleID = sc.nextLine().trim().toUpperCase(Locale.ROOT);

                        System.out.print("VehicleType (e.g., Sedan / SUV / Truck): ");
                        String vehicleType = sc.nextLine().trim();

                        System.out.print("Quantity (vehicleNumber): ");
                        int quantity;
                        try {
                            quantity = Integer.parseInt(sc.nextLine().trim());
                        } catch (NumberFormatException nfe) {
                            System.out.println("Invalid quantity. Must be an integer.");
                            break;
                        }

                        System.out.print("Reservation price (e.g., 120.0): ");
                        double price;
                        try {
                            price = Double.parseDouble(sc.nextLine().trim());
                        } catch (NumberFormatException nfe) {
                            System.out.println("Invalid price. Must be a number.");
                            break;
                        }

                        log(session.managerID, "SEND addVehicle vehicleID=" + vehicleID + " type=" + vehicleType +
                                " qty=" + quantity + " price=" + price);
                        String resp = session.stub.addVehicle(session.managerID, quantity, vehicleType, vehicleID, price);
                        log(session.managerID, "RECV addVehicle | " + resp);
                        safePrint("Add/Update Vehicle", resp);

                        // Auto list after add/update
                        doList(session.stub, session.managerID);
                    }
                    case "2" -> {
                        System.out.print("VehicleID to remove (e.g., MTL1001): ");
                        String vehicleID = sc.nextLine().trim().toUpperCase(Locale.ROOT);

                        log(session.managerID, "SEND removeVehicle vehicleID=" + vehicleID);
                        String resp = session.stub.removeVehicle(session.managerID, vehicleID);
                        log(session.managerID, "RECV removeVehicle | " + resp);
                        safePrint("Remove Vehicle", resp);

                        // Auto list after remove
                        doList(session.stub, session.managerID);
                    }
                    case "3" -> doList(session.stub, session.managerID);

                    case "9" -> {
                        log(session.managerID, "LOGOUT");
                        System.out.println("\n--- Switching manager ---");
                        session = login(sc);
                    }

                    default -> System.out.println("Unknown option. Please choose 0,1,2,3,9.");
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
