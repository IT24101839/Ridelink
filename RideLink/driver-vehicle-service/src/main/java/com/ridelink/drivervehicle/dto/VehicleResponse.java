package com.ridelink.drivervehicle.dto;

import com.ridelink.drivervehicle.entity.VehicleStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Vehicle details returned by the service")
public record VehicleResponse(
        @Schema(description = "Vehicle identifier", example = "1") Long id,
        @Schema(description = "Unique vehicle registration number", example = "WP CAB-1234") String registrationNumber,
        @Schema(description = "Vehicle class or type", example = "SEDAN") String vehicleType,
        @Schema(description = "Vehicle make and model", example = "Toyota Prius") String model,
        @Schema(description = "Current vehicle status", example = "ACTIVE") VehicleStatus status,
        @Schema(description = "ID of the owning driver", example = "1") Long driverId
) {
}
