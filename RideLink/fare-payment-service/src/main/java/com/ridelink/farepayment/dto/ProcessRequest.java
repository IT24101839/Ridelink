package com.ridelink.farepayment.dto;
import jakarta.validation.constraints.NotNull;
public record ProcessRequest(@NotNull Boolean success) {}
