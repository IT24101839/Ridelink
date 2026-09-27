package com.ridelink.drivervehicle.service;

public class DriverNotFoundException extends RuntimeException {
    public DriverNotFoundException(Long id) {
        super("Driver not found with id: " + id);
    }
}
