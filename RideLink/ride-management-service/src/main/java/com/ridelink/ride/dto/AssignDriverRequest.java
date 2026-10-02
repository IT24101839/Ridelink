package com.ridelink.ride.dto;

import jakarta.validation.constraints.NotBlank;

public record AssignDriverRequest(@NotBlank String driverId) {
}
