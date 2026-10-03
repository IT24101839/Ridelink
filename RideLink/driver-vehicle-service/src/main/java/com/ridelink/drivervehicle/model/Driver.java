package com.ridelink.drivervehicle.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "drivers")
public class Driver {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private String id;
    @Column(nullable = false, unique = true) private String accountId;
    private String fullName;
    private String email;
    private String phone;
    @Column(nullable = false, unique = true) private String licenseNumber;
    @Enumerated(EnumType.STRING) private DriverStatus status = DriverStatus.OFFLINE;
    @ElementCollection(fetch = FetchType.EAGER) private Set<String> serviceAreas = new HashSet<>();
    private Double currentLat;
    private Double currentLng;
    private double rating;
    private long totalRides;
    private Instant createdAt = Instant.now();
    @JsonIgnore private String reservedRideId;
    @Version @JsonIgnore private Long version;

    protected Driver() {}
    public Driver(String accountId, String fullName, String email, String phone, String licenseNumber, Set<String> areas) {
        this.accountId=accountId; this.fullName=fullName; this.email=email; this.phone=phone;
        this.licenseNumber=licenseNumber; this.serviceAreas=new HashSet<>(areas);
    }
    public String getId(){return id;}
    public String getAccountId(){return accountId;}
    public String getFullName(){return fullName;}
    public String getEmail(){return email;}
    public String getPhone(){return phone;}
    public String getLicenseNumber(){return licenseNumber;}
    public DriverStatus getStatus(){return status;}
    public Set<String> getServiceAreas(){return Set.copyOf(serviceAreas);}
    public Double getCurrentLat(){return currentLat;}
    public Double getCurrentLng(){return currentLng;}
    public double getRating(){return rating;}
    public long getTotalRides(){return totalRides;}
    public Instant getCreatedAt(){return createdAt;}
    @JsonIgnore public String getReservedRideId(){return reservedRideId;}
    public void availability(DriverStatus status){this.status=status;}
    public void location(double lat,double lng){currentLat=lat;currentLng=lng;}
    public void areas(Set<String> areas){serviceAreas=new HashSet<>(areas);}
    public void reserve(String rideId){reservedRideId=rideId;status=DriverStatus.BUSY;}
    public void release(){reservedRideId=null;if(status==DriverStatus.BUSY)status=DriverStatus.AVAILABLE;}
}
