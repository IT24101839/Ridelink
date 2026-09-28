package com.ridelink.drivervehicle.dto;

import com.ridelink.drivervehicle.entity.VehicleStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Vehicle details returned by the service")
public record VehicleResponse(
        @Schema(description = "MongoDB ObjectId for the vehicle", example = "66a1b2c3d4e5f60718293a4c") String id,
        @Schema(description = "Unique vehicle registration number", example = "WP CAB-1234") String registrationNumber,
        @Schema(description = "Vehicle class or type", example = "SEDAN") String vehicleType,
        @Schema(description = "Vehicle make and model", example = "Toyota Prius") String model,
        @Schema(description = "Current vehicle status", example = "ACTIVE") VehicleStatus status,
        @Schema(description = "MongoDB ObjectId of the owning driver", example = "66a1b2c3d4e5f60718293a4b") String driverId
) {
}
