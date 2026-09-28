package com.ridelink.drivervehicle.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Driver current location update")
public record LocationUpdateRequest(
        @Schema(description = "Latitude in decimal degrees, from -90 to 90", example = "6.9271")
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,

        @Schema(description = "Longitude in decimal degrees, from -180 to 180", example = "79.8612")
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude
) {
}