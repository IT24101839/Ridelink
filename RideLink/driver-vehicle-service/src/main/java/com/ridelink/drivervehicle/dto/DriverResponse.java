package com.ridelink.drivervehicle.dto;

import com.ridelink.drivervehicle.entity.DriverStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Driver details returned by the service")
public record DriverResponse(
        @Schema(description = "Driver identifier", example = "1") Long id,
        @Schema(description = "Driver's full name", example = "Ayesha Perera") String name,
        @Schema(description = "Contact phone number", example = "+94771234567") String phone,
        @Schema(description = "Unique contact email", example = "ayesha.perera@example.com") String email,
        @Schema(description = "Current availability status", example = "UNAVAILABLE") DriverStatus status,
        @Schema(description = "Driver's service area", example = "Colombo") String serviceArea,
        @Schema(description = "Current latitude", example = "6.9271") Double currentLatitude,
        @Schema(description = "Current longitude", example = "79.8612") Double currentLongitude
) {
}
