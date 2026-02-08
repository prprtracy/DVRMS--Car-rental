package common;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface DVRMS extends Remote {

    //manager role;
    String addVehicle(String managerID,int vehicleNumber, String vehicleType,
                      String vehicleID, double reservationPrice) throws RemoteException;
    String removeVehicle(String managerID, String vehicleID) throws RemoteException;
    String listAvailableVehicle(String managerID) throws RemoteException;

    //customer role;

    String reserveVehicle(String customerID, String vehicleID, String startDate, String endDate, boolean wantWaitlist) throws RemoteException;

    String updateReservation(String customerID, String vehicleID, String startDate, String endDate) throws RemoteException;
    String cancelReservation(String customerID, String vehicleID) throws RemoteException;
    String findVehicle(String customerID, String vehicleType) throws RemoteException;
    String getBudget(String customerID) throws RemoteException;




}
