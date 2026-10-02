package com.ridelink.ride.dto;

import com.ridelink.ride.model.FareDetails;
import com.ridelink.ride.model.RideStatus;

public record PaymentContext(String rideId, String riderId, String driverId,
                             RideStatus status, FareDetails fare) {
}
