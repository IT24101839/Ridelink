package com.ridelink.drivervehicle.entity;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "drivers")
public class Driver {
    @Id
    private String id;

    @NotBlank @Size(max = 100)
    private String name;

    @NotBlank @Pattern(regexp = "^[+0-9() .-]{7,20}$")
    @Indexed(unique = true)
    private String phone;

    @NotBlank @Email @Size(max = 254)
    @Indexed(unique = true)
    private String email;

    @NotNull
    private DriverStatus status = DriverStatus.UNAVAILABLE;

    @NotBlank @Size(max = 150)
    private String serviceArea;

    @NotNull @DecimalMin("-90.0") @DecimalMax("90.0")
    private Double currentLatitude;

    @NotNull @DecimalMin("-180.0") @DecimalMax("180.0")
    private Double currentLongitude;

    public Driver() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public DriverStatus getStatus() { return status; }
    public void setStatus(DriverStatus status) { this.status = status; }
    public String getServiceArea() { return serviceArea; }
    public void setServiceArea(String serviceArea) { this.serviceArea = serviceArea; }
    public Double getCurrentLatitude() { return currentLatitude; }
    public void setCurrentLatitude(Double currentLatitude) { this.currentLatitude = currentLatitude; }
    public Double getCurrentLongitude() { return currentLongitude; }
    public void setCurrentLongitude(Double currentLongitude) { this.currentLongitude = currentLongitude; }
}
