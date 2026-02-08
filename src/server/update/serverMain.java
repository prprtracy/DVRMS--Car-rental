package server.update;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class serverMain {
    public static void main(String[] args) {
        try{
            Registry reg = LocateRegistry.createRegistry(1099);
            reg.rebind("MTL", new officeServer("MTL"));
            reg.rebind("WPG", new officeServer("WPG"));
            reg.rebind("BNF", new officeServer("BNF"));
            System.out.println("RMI registry ready. Bound: MTL, WPG, BNF");


        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
