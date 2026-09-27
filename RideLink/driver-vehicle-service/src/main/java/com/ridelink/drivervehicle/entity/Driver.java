package com.ridelink.drivervehicle.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "drivers")
public class Driver {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String name;

    @NotBlank @Pattern(regexp = "^[+0-9() .-]{7,20}$")
    @Column(nullable = false, unique = true, length = 20)
    private String phone;

    @NotBlank @Email @Size(max = 254)
    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @NotNull @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DriverStatus status = DriverStatus.UNAVAILABLE;

    @NotBlank @Size(max = 150)
    @Column(nullable = false, length = 150)
    private String serviceArea;

    @NotNull @DecimalMin("-90.0") @DecimalMax("90.0")
    @Column(nullable = false)
    private Double currentLatitude;

    @NotNull @DecimalMin("-180.0") @DecimalMax("180.0")
    @Column(nullable = false)
    private Double currentLongitude;

    @OneToMany(mappedBy = "driver", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Vehicle> vehicles = new ArrayList<>();

    public Driver() {}

    public Long getId() { return id; }
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
    public List<Vehicle> getVehicles() { return vehicles; }

    public void addVehicle(Vehicle vehicle) {
        vehicles.add(vehicle);
        vehicle.setDriver(this);
    }

    public void removeVehicle(Vehicle vehicle) {
        vehicles.remove(vehicle);
        vehicle.setDriver(null);
    }
}
