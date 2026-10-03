package com.ridelink.drivervehicle.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Driver service area update")
public record ServiceAreaUpdateRequest(
        @Schema(description = "Area in which the driver provides service", example = "Colombo")
        @NotBlank @Size(max = 150) String serviceArea
) {
}