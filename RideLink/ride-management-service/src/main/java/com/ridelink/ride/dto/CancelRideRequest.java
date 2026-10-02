package com.ridelink.ride.dto;

import jakarta.validation.constraints.Size;

public record CancelRideRequest(@Size(max = 500) String reason) {
}
