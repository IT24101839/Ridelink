package com.ridelink.drivervehicle.service;
import com.ridelink.drivervehicle.model.*;
import com.ridelink.drivervehicle.dto.DriverRequests.*;
import com.ridelink.drivervehicle.repository.*;
import com.ridelink.drivervehicle.security.UserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;

@Service
@Transactional
public class DriverService {
    private final DriverRepository drivers;
    private final VehicleRepository vehicles;
    public DriverService(DriverRepository drivers,VehicleRepository vehicles){this.drivers=drivers;this.vehicles=vehicles;}
    public Driver register(UserPrincipal user,Register request){
        if(drivers.findByAccountId(user.userId()).isPresent())throw conflict("Driver profile already exists");
        return drivers.saveAndFlush(new Driver(user.userId(),request.fullName(),request.email(),request.phone(),
                request.licenseNumber(),request.serviceAreas()));
    }
    public Driver get(String id){return drivers.findById(id).orElseThrow(()->missing("Driver not found"));}
    public Driver byAccount(String id){return drivers.findByAccountId(id).orElseThrow(()->missing("Driver not found"));}
    public Driver owned(String id,UserPrincipal user){Driver d=get(id);authorize(d,user);return d;}
    public Driver ownedAccount(String id,UserPrincipal user){
        if(!user.isAdmin()&&!user.userId().equals(id))throw forbidden();
        return byAccount(id);
    }
    public void authorize(Driver d,UserPrincipal user){
        if(!user.isAdmin()&&!d.getAccountId().equals(user.userId()))throw forbidden();
    }
    private Driver locked(String id){return drivers.lockById(id).orElseThrow(()->missing("Driver not found"));}
    public Driver availability(String id,Availability request,UserPrincipal user){
        Driver d=locked(id);authorize(d,user);
        if(request.status()==DriverStatus.BUSY)throw conflict("BUSY is controlled by ride reservations");
        if(request.status()==DriverStatus.AVAILABLE){
            if(d.getReservedRideId()!=null)throw conflict("Driver has an active reservation");
            if(!vehicles.existsByDriverIdAndActiveTrue(id))throw conflict("An active vehicle is required");
        }
        d.availability(request.status());return d;
    }
    public Driver location(String id,Location request,UserPrincipal user){
        Driver d=locked(id);authorize(d,user);d.location(request.currentLat(),request.currentLng());return d;
    }
    public Driver areas(String id,Areas request,UserPrincipal user){
        Driver d=locked(id);authorize(d,user);d.areas(request.serviceAreas());return d;
    }
    public Vehicle addVehicle(VehicleRequest r,UserPrincipal user){
        owned(r.driverId(),user);
        return vehicles.saveAndFlush(new Vehicle(r.driverId(),r.plateNumber(),r.make(),r.model(),r.year(),r.color(),r.type(),r.seatCapacity()));
    }
    public List<Vehicle> vehicles(String driverId,UserPrincipal user){owned(driverId,user);return vehicles.findByDriverId(driverId);}
    public List<Driver> available(){
        return drivers.findByStatusAndReservedRideIdIsNull(DriverStatus.AVAILABLE).stream()
                .filter(d->vehicles.existsByDriverIdAndActiveTrue(d.getId())).toList();
    }
    public Driver reserve(String id,String rideId){
        Driver d=locked(id);
        if(rideId.equals(d.getReservedRideId()))return d;
        if(d.getReservedRideId()!=null||d.getStatus()!=DriverStatus.AVAILABLE
                ||!vehicles.existsByDriverIdAndActiveTrue(id))throw conflict("Driver unavailable");
        d.reserve(rideId);return d;
    }
    public Driver release(String id,String rideId){
        Driver d=locked(id);
        if(d.getReservedRideId()==null)return d;
        if(!rideId.equals(d.getReservedRideId()))throw conflict("Reservation belongs to another ride");
        d.release();return d;
    }
    private ResponseStatusException conflict(String m){return new ResponseStatusException(HttpStatus.CONFLICT,m);}
    private ResponseStatusException missing(String m){return new ResponseStatusException(HttpStatus.NOT_FOUND,m);}
    private ResponseStatusException forbidden(){return new ResponseStatusException(HttpStatus.FORBIDDEN,"Access denied");}
}
