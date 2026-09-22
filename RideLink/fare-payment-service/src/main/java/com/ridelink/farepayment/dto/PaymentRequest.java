package com.ridelink.farepayment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PaymentRequest(
        @NotBlank String fareId,
        @NotNull Long rideId,
        @NotNull Long userId,
        @NotBlank String paymentMethod
) {
}
