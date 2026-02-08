package Test;

import common.DVRMS;
import common.IdCheck;
import common.Logger;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class managerTest {

    private static void log(String managerID, String msg) {
        Logger.log("logs/client_" + managerID + ".txt", msg);
    }

    private static String call(String managerID, String op, java.util.concurrent.Callable<String> fn) throws Exception {
        log(managerID, "SEND " + op);
        String res = fn.call();
        log(managerID, "RECV " + op + " | " + res);
        return res;
    }

    private static DVRMS stubOf(Registry reg, String office) throws Exception {
        return (DVRMS) reg.lookup(office);
    }

    public static void main(String[] args) throws Exception {
        String managerID = "MTLM1111";
        String office = IdCheck.officeLocation(managerID);

        Registry reg = LocateRegistry.getRegistry("localhost", 1099);

        DVRMS mtl = stubOf(reg, "MTL");
        DVRMS wpg = stubOf(reg, "WPG");
        DVRMS bnf = stubOf(reg, "BNF");

        System.out.println("===== Manager Setup Start =====");

        // -------- MTL --------
        System.out.println("\n--- MTL add ---");
        System.out.println(mtl.addVehicle("MTLM1111", 1, "Sedan", "MTL1001", 120));
        System.out.println(mtl.addVehicle("MTLM1111", 1, "Sedan", "MTL1002", 130));
        System.out.println(mtl.addVehicle("MTLM1111", 3, "Truck", "MTL9999", 1500));
        System.out.println("MTL list:\n" + mtl.listAvailableVehicle("MTLM1111"));

        // -------- WPG --------
        System.out.println("\n--- WPG add ---");
        System.out.println(wpg.addVehicle("WPGM1111", 1, "Sedan", "WPG2001", 110));
        System.out.println(wpg.addVehicle("WPGM1111", 1, "Sedan", "WPG2002", 115));
        System.out.println(wpg.addVehicle("WPGM1111", 5, "SUV",   "WPG2003", 210));
        System.out.println("WPG list:\n" + wpg.listAvailableVehicle("WPGM1111"));

        // -------- BNF --------
        System.out.println("\n--- BNF add ---");
        System.out.println(bnf.addVehicle("BNFM1111", 1, "Sedan", "BNF3001", 105));
        System.out.println(bnf.addVehicle("BNFM1111", 1, "Sedan", "BNF3002", 108));
        System.out.println(bnf.addVehicle("BNFM1111", 1, "SUV",   "BNF3003", 220));
        System.out.println("BNF list:\n" + bnf.listAvailableVehicle("BNFM1111"));

        System.out.println("\n===== Manager Setup End =====");
    }
}
