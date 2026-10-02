package com.ridelink.ride.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record CompleteRideRequest(
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal distanceKm) {
}
