package com.ridelink.drivervehicle.dto;
import com.ridelink.drivervehicle.model.DriverStatus;
import jakarta.validation.constraints.*;
import java.util.Set;
public final class DriverRequests {
    private DriverRequests(){}
    public record Register(@NotBlank @Size(max=200) String fullName,
            @NotBlank @Email String email,@NotBlank @Size(max=40) String phone,
            @NotBlank @Size(max=100) String licenseNumber,
            @NotNull @Size(max=100) Set<@NotBlank @Size(max=100) String> serviceAreas){}
    public record Availability(@NotNull DriverStatus status){}
    public record Location(@NotNull @DecimalMin("-90") @DecimalMax("90") Double currentLat,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double currentLng){}
    public record Areas(@NotNull @Size(max=100) Set<@NotBlank @Size(max=100) String> serviceAreas){}
    public record Reservation(@NotBlank @Size(max=100) String rideId){}
    public record VehicleRequest(@NotBlank String driverId,@NotBlank @Size(max=40) String plateNumber,
            @NotBlank String make,@NotBlank String model,@Min(1886) @Max(2100) int year,
            @NotBlank String color,@NotBlank String type,@Min(1) @Max(100) int seatCapacity){}
}
