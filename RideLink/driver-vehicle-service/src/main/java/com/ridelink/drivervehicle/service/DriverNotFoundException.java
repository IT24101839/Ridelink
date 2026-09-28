package com.ridelink.drivervehicle.service;

public class DriverNotFoundException extends RuntimeException {
    public DriverNotFoundException(String id) {
        super("Driver not found with id: " + id);
    }
}
