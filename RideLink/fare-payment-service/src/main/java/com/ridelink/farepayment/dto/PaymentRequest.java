package com.ridelink.farepayment.dto;
import jakarta.validation.constraints.*;
public record PaymentRequest(@NotBlank String rideId,@NotBlank @Size(max=40) String paymentMethod) {}
