package com.ridelink.ride.model;

import jakarta.validation.constraints.*;

public record Location(
        @NotBlank @Size(max = 500) String address,
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude) {
}
