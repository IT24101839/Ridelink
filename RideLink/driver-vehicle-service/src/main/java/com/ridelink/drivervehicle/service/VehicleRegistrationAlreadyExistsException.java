package com.ridelink.drivervehicle.service;

public class VehicleRegistrationAlreadyExistsException extends RuntimeException {
    public VehicleRegistrationAlreadyExistsException(String registrationNumber) {
        super("A vehicle with registration number " + registrationNumber + " already exists");
    }
}
