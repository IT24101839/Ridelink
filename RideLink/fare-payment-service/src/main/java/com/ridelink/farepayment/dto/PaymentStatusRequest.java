package com.ridelink.farepayment.dto;

import com.ridelink.farepayment.model.PaymentStatus;
import jakarta.validation.constraints.NotNull;

public record PaymentStatusRequest(@NotNull PaymentStatus status) {
}
