package com.ridelink.drivervehicle.dto;

import com.ridelink.drivervehicle.entity.VehicleStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Vehicle information used to create or update a vehicle")
public record VehicleRequest(
        @Schema(description = "Unique vehicle registration number", example = "WP CAB-1234")
        @NotBlank @Size(max = 20) String registrationNumber,

        @Schema(description = "Vehicle class or type", example = "SEDAN")
        @NotBlank @Size(max = 50) String vehicleType,

        @Schema(description = "Vehicle make and model", example = "Toyota Prius")
        @NotBlank @Size(max = 100) String model,

        @Schema(description = "Vehicle status; defaults to ACTIVE when omitted", example = "ACTIVE")
        VehicleStatus status,

        @Schema(description = "MongoDB ObjectId of an existing driver who owns this vehicle", example = "66a1b2c3d4e5f60718293a4b")
        @NotBlank String driverId
) {
}
