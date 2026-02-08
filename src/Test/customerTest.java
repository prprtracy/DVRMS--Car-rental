package Test;

import common.DVRMS;
import common.IdCheck;
import common.Logger;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class customerTest {

    private static void log(String userID, String msg) {
        Logger.log("logs/client_" + userID + ".txt", msg);
    }

    private static String call(String userID, String op, java.util.concurrent.Callable<String> fn) throws Exception {
        log(userID, "SEND " + op);
        String res = fn.call();
        log(userID, "RECV " + op + " | " + res);
        return res;
    }

    public static void main(String[] args) throws Exception {
        String customerID = "MTLU1111";
        String office = IdCheck.officeLocation(customerID);

        Registry reg = LocateRegistry.getRegistry("localhost", 1099);
        DVRMS stub = (DVRMS) reg.lookup(office); // office = IdCheck.officeLocation(customerID)

        System.out.println("===== Customer Test Start =====");
        System.out.println("customerID=" + customerID + " office=" + office);

        // ------------------------------
        // Local Testing
        // ------------------------------
        System.out.println("===== Local Testing =====");

        // baseline: reserve success
        System.out.println("\n[1] Reserve success (MTL1001 01-05 Feb 2026)");
        System.out.println(stub.reserveVehicle(customerID, "MTL1001", "01022026", "05022026", true));
        System.out.println("\nShow budget after reserve");
        System.out.println(stub.getBudget(customerID));

        // overlap should fail
        System.out.println("\n[2] Reserve overlap should fail (MTL1001 03-04 Feb 2026)");
        System.out.println(stub.reserveVehicle(customerID, "MTL1001", "03022026", "04022026", true));

        // updateReservation tests
        System.out.println("\n[3] Update reservation success (move to 06-07 Feb 2026)");
        System.out.println(stub.updateReservation(customerID, "MTL1001", "06022026", "07022026"));

        // update invalid date (end < start) should fail
        System.out.println("\n[4] Update invalid range should fail (end < start)");
        System.out.println(stub.updateReservation(customerID, "MTL1001", "08022026", "07022026"));

        // update invalid format should fail
        System.out.println("\n[5] Update invalid format should fail (bad date)");
        System.out.println(stub.updateReservation(customerID, "MTL1001", "99999999", "01032026"));


        // cancelReservation tests
        System.out.println("\n[6] Cancel reservation success (MTL1001)");
        System.out.println(stub.cancelReservation(customerID, "MTL1001"));

        System.out.println("\nShow budget after cancel (refund applied)");
        System.out.println(stub.getBudget(customerID));


        // after cancel, reserve a period that was previously blocked should succeed
        System.out.println("\n[7] Reserve after cancel should succeed (MTL1111 06-07 Feb 2026)");
        System.out.println(stub.reserveVehicle("MTLU1111", "MTL1001", "06022026", "07022026", true));

        System.out.println("\n[8] Reserve after cancel should fail (MTL1001 03-04 Feb 2026)");
        System.out.println(stub.reserveVehicle(customerID, "MTL1001", "03022026", "04022026", true));

        //budget not enough
        System.out.println("\n[9] Too expensive should fail (MTL9999)");
        System.out.println(stub.reserveVehicle(customerID, "MTL9999", "06022026", "07022026", true));

        // cancel non-existent reservation should fail
        System.out.println("\n[10] Cancel non-existent reservation should fail (MTL1002)");
        System.out.println(stub.cancelReservation(customerID, "MTL1002"));

        // reserve invalid date should fail
        System.out.println("\n[11] Reserve invalid date should fail (bad date)");
        System.out.println(stub.reserveVehicle(customerID, "MTL1001", "32132026", "01022026", true));

        // ------------------------------
        // UPD testing across the office
        // ------------------------------
        System.out.println("===== UDP Testing =====");

        System.out.println("\n[1] FIND Sedan across 3 offices (UDP expected)");
        System.out.println(stub.findVehicle(customerID, "Sedan"));

        System.out.println("\n[2] Remote reserve WPG2001 (should succeed, UDP RESERVE)");
        System.out.println(stub.reserveVehicle(customerID, "WPG2001", "06022026", "07022026", true));

        System.out.println("\n[3] Remote reserve another WPG2002 (should FAIL: only 1 per remote office)");
        System.out.println(stub.reserveVehicle(customerID, "WPG2002", "08022026", "09022026", true));

        System.out.println("\n[4] Remote reserve BNF3001 (should succeed)");
        System.out.println(stub.reserveVehicle(customerID, "BNF3001", "10022026", "11022026", true));

        System.out.println("\n[5] Remote reserve another BNF3002 (should FAIL: only 1 per remote office)");
        System.out.println(stub.reserveVehicle(customerID, "BNF3002", "12022026", "13022026", true));

        // ------------------------------
        // waitingList test
        // ------------------------------
        System.out.println("\n===== WaitList Testing =====");

        String custA = "MTLU1111";
        String custB = "MTLU2222";
        String customerC = "MTLU3333";
        String customerD = "MTLU4444";

        // 1) A sucessed
        System.out.println("\n[1] A reserves MTL1001 (01032026-05032026) should succeed");
        System.out.println(stub.reserveVehicle(custA, "MTL1001", "01032026", "05032026", true));

        // 2) B reserve same time -> overlaps -> should add to waitlist
        System.out.println("\n[2] B reserves same slot -> should be added to waitlist");
        System.out.println(stub.reserveVehicle(custB, "MTL1001", "01032026", "05032026", true));

        // 3) A cancel -> should auto-assign to B（cancel 返回里应有 Auto-assigned: 1）
        System.out.println("\n[3] A cancels MTL1001 -> should auto-assign to B from waitlist");
        System.out.println(stub.cancelReservation(custA, "MTL1001"));

        // 4) check when B is using ：A reserve same time should fail
        System.out.println("\n[4] A tries to reserve same slot again -> should FAIL (now held by B)");
        System.out.println(stub.reserveVehicle(custA, "MTL1001", "01032026", "05032026", true));

        // A 先占住 MTL1002
        String vehicle = "MTL1002";

        // 1) A reserve 成功
        System.out.println("\n[5] A reserves " + vehicle + " (01042026-05042026) should succeed");
        System.out.println(stub.reserveVehicle(custA, vehicle, "01042026", "05042026", true));

        // 2) B reserve 同一时间 -> 进 waitlist
        System.out.println("\n[6] B reserves same slot -> should be added to waitlist");
        System.out.println(stub.reserveVehicle(custB, vehicle, "01042026", "05042026", true));

        // 3) manager addVehicle（增加数量/更新信息）-> 应触发 auto-assign
        // 注意：customerTest 里要 lookup MTL stub 作为 manager 调用 addVehicle
        DVRMS mtlStub = (DVRMS) reg.lookup("MTL");
        System.out.println("\n[7] Manager increases vehicleNumber for " + vehicle + " -> should auto-assign 1 from waitlist");
        System.out.println(mtlStub.addVehicle("MTLM1111", 1, "Sedan", vehicle, 130));

        String newVehicle = "MTL2000";

        System.out.println("\n[8] Manager creates NEW vehicle " + newVehicle + " with inventory=1");
        System.out.println(mtlStub.addVehicle("MTLM1111", 1, "Sedan", newVehicle, 130));

        System.out.println("\n[9] A reserves " + newVehicle + " (01042026-05042026) should succeed");
        System.out.println(stub.reserveVehicle(custA, newVehicle, "01042026", "05042026", true));

        // ------------------------------
        // Manager removeVehicle test (active reservation + waitlist handling)
        // ------------------------------
        System.out.println("\n===== Remove Vehicle Testing =====");

        DVRMS mtlStub2 = (DVRMS) reg.lookup("MTL");
        String mID = "MTLM1111";

        System.out.println("\n[1] Manager creates vehicle " + "MTL2001");
        System.out.println(mtlStub2.addVehicle(mID, 1, "Sedan", "MTL2001", 120));

        System.out.println("\n[RV-B0] Budget before anything (C, D)");
        System.out.println("C: " + mtlStub.getBudget(customerC));
        System.out.println("D: " + mtlStub.getBudget(customerD));

        System.out.println("\n[RV-1] C reserve " + "MTL2001" + " 01-05 Jun 2026 - Success");
        System.out.println(mtlStub.reserveVehicle(customerC, "MTL2001", "01062026", "05062026", true));

        System.out.println("\n[RV-B0] Budget after reserve the vehicle ");
        System.out.println("C: " + mtlStub.getBudget(customerC));

        // RV-2: B reserves same slot -> waitlist
        System.out.println("\n[RV-2] D reserve " + "MTL2001" + " 01-05 Jun 2026 - Fail (waiting list)");
        System.out.println(mtlStub.reserveVehicle(customerD, "MTL2001", "01062026", "05062026",true));


        // Remove vehicle: should remove record and handle active/waitlist customers
        System.out.println("\n[2] Manager removes vehicle " + "MTL2001" + " (should succeed and handle reservations/waitlist)");
        String res = mtlStub.removeVehicle(mID, "MTL2001");
        System.out.println(res);
        if (res.contains("Removed waitlist")) {
            System.out.println("[CHECK] Waiting list cleared");
        }

        System.out.println("\n[RV-B0] Budget after remove the vehicle ");
        System.out.println("C: " + mtlStub.getBudget(customerC));

        // After removal, reserve should say no such vehicle (or fail)
        System.out.println("\n[3] Try reserve removed vehicle (should FAIL: no such vehicle)");
        System.out.println(stub.reserveVehicle(customerC, "MTL2001", "06062026", "07062026", true));

        System.out.println("\n===== Customer Test End =====");
    }
}
