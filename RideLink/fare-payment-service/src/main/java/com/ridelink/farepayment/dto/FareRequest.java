package com.ridelink.farepayment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record FareRequest(
        @NotNull Long rideId,
        @NotNull @DecimalMin("0.0") BigDecimal distanceKm,
        @NotNull @DecimalMin("0") Integer durationMinutes
) {
}
