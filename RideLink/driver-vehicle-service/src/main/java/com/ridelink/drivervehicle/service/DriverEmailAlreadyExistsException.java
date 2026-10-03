package com.ridelink.drivervehicle.service;

public class DriverEmailAlreadyExistsException extends RuntimeException {
    public DriverEmailAlreadyExistsException(String email) {
        super("A driver with email " + email + " already exists");
    }
}
