package com.ridelink.account.exception;

public class AdminRegistrationException extends RuntimeException {
    public AdminRegistrationException() {
        super("ADMIN role cannot be registered publicly");
    }
}
