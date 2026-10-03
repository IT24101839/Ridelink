package com.ridelink.drivervehicle.dto;

import com.ridelink.drivervehicle.entity.DriverStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Driver availability update")
public record AvailabilityUpdateRequest(
        @Schema(description = "New driver availability status", example = "AVAILABLE")
        @NotNull DriverStatus status
) {
}