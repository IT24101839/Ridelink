package com.ridelink.drivervehicle.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;
import java.time.Instant;

@Document("vehicles")
public class Vehicle {
    @Id private String id = java.util.UUID.randomUUID().toString();
    @Indexed private String driverId;
    @Indexed(unique = true) private String plateNumber;
    private String make;
    private String model;
    private int year;
    private String color;
    private String type;
    private int seatCapacity;
    private boolean active=true;
    private Instant createdAt=Instant.now();
    protected Vehicle(){}
    public Vehicle(String driverId,String plateNumber,String make,String model,int year,String color,String type,int seatCapacity){
        this.driverId=driverId;this.plateNumber=plateNumber;this.make=make;this.model=model;
        this.year=year;this.color=color;this.type=type;this.seatCapacity=seatCapacity;
    }
    public String getId(){return id;}
    public String getDriverId(){return driverId;}
    public String getPlateNumber(){return plateNumber;}
    public String getMake(){return make;}
    public String getModel(){return model;}
    public int getYear(){return year;}
    public String getColor(){return color;}
    public String getType(){return type;}
    public int getSeatCapacity(){return seatCapacity;}
    public boolean isActive(){return active;}
    public Instant getCreatedAt(){return createdAt;}
}
