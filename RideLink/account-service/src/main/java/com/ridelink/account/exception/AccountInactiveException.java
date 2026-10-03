package com.ridelink.account.exception;

public class AccountInactiveException extends RuntimeException {
    public AccountInactiveException() {
        super("Account is deactivated");
    }
    public AccountInactiveException(String message) {
        super(message);
    }
}
