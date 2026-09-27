package com.ridelink.drivervehicle.dto;

import com.ridelink.drivervehicle.entity.DriverStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Driver information used to create or update a driver")
public record DriverRequest(
        @Schema(description = "Driver's full name", example = "Ayesha Perera")
        @NotBlank @Size(max = 100) String name,

        @Schema(description = "Contact phone number", example = "+94771234567")
        @NotBlank @Pattern(regexp = "^[+0-9() .-]{7,20}$") String phone,

        @Schema(description = "Unique contact email", example = "ayesha.perera@example.com")
        @NotBlank @Email @Size(max = 254) String email,

        @Schema(description = "Availability; defaults to UNAVAILABLE when omitted", example = "UNAVAILABLE")
        DriverStatus status,

        @Schema(description = "Area in which the driver provides service", example = "Colombo")
        @NotBlank @Size(max = 150) String serviceArea,

        @Schema(description = "Current latitude in decimal degrees", example = "6.9271")
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double currentLatitude,

        @Schema(description = "Current longitude in decimal degrees", example = "79.8612")
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double currentLongitude
) {
}
