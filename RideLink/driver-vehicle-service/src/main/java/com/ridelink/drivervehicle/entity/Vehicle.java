package com.ridelink.drivervehicle.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "vehicles")
public class Vehicle {
    @Id
    private String id;

    @NotBlank @Size(max = 20)
    @Indexed(unique = true)
    private String registrationNumber;

    @NotBlank @Size(max = 50)
    private String vehicleType;

    @NotBlank @Size(max = 100)
    private String model;

    @NotNull
    private VehicleStatus status = VehicleStatus.ACTIVE;

    @NotBlank
    private String driverId;

    public Vehicle() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getRegistrationNumber() { return registrationNumber; }
    public void setRegistrationNumber(String registrationNumber) { this.registrationNumber = registrationNumber; }
    public String getVehicleType() { return vehicleType; }
    public void setVehicleType(String vehicleType) { this.vehicleType = vehicleType; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public VehicleStatus getStatus() { return status; }
    public void setStatus(VehicleStatus status) { this.status = status; }
    public String getDriverId() { return driverId; }
    public void setDriverId(String driverId) { this.driverId = driverId; }
}
