package com.ridelink.drivervehicle.service;

public class InvalidMongoIdException extends RuntimeException {
    public InvalidMongoIdException(String id) {
        super("Invalid MongoDB ObjectId: " + id);
    }
}
