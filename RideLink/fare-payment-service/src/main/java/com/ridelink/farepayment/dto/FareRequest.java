package com.ridelink.farepayment.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record FareRequest(@NotBlank String rideId,
        @NotNull @DecimalMin(value="0",inclusive=false) BigDecimal distanceKm,@Min(0) long durationMinutes) {}
