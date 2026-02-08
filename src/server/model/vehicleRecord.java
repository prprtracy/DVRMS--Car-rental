package server.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

public class vehicleRecord {
    public final String vehicleID;
    public String vehicleType;
    public int vehicleNumber;
    public double price;

    public final List<Reservation> reservations = new ArrayList<>();
    public final Deque<WaitingList> waitlist = new ArrayDeque<>();
    public final ReentrantLock lock = new ReentrantLock(true);

    public vehicleRecord(String vehicleID, String vehicleType, int vehicleNumber, double price) {
        this.vehicleID = vehicleID;
        this.vehicleType = vehicleType;
        this.vehicleNumber = vehicleNumber;
        this.price = price;
    }
}
