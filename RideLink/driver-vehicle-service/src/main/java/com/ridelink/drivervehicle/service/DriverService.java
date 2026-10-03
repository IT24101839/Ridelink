package com.ridelink.drivervehicle.service;
import com.ridelink.drivervehicle.model.*;
import com.ridelink.drivervehicle.dto.DriverRequests.*;
import com.ridelink.drivervehicle.repository.*;
import com.ridelink.drivervehicle.security.UserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;

@Service
public class DriverService {
    private final DriverRepository drivers;
    private final VehicleRepository vehicles;
    private final MongoTemplate mongo;
    public DriverService(DriverRepository drivers,VehicleRepository vehicles,MongoTemplate mongo){this.drivers=drivers;this.vehicles=vehicles;this.mongo=mongo;}
    public Driver register(UserPrincipal user,Register request){
        if(drivers.findByAccountId(user.userId()).isPresent())throw conflict("Driver profile already exists");
        return drivers.insert(new Driver(user.userId(),request.fullName(),request.email(),request.phone(),
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
    public Driver availability(String id,Availability request,UserPrincipal user){
        Driver d=get(id);authorize(d,user);
        if(request.status()==DriverStatus.BUSY)throw conflict("BUSY is controlled by ride reservations");
        if(request.status()==DriverStatus.AVAILABLE){
            if(d.getReservedRideId()!=null)throw conflict("Driver has an active reservation");
            if(!vehicles.existsByDriverIdAndActiveTrue(id))throw conflict("An active vehicle is required");
        }
        Criteria condition=Criteria.where("id").is(id);
        if(request.status()==DriverStatus.AVAILABLE)condition.and("reservedRideId").is(null);
        Driver updated=modify(condition,new Update().set("status",request.status()));
        if(updated==null)throw conflict("Driver has an active reservation");
        return updated;
    }
    public Driver location(String id,Location request,UserPrincipal user){
        Driver d=get(id);authorize(d,user);return modify(Criteria.where("id").is(id),new Update().set("currentLat",request.currentLat()).set("currentLng",request.currentLng()));
    }
    public Driver areas(String id,Areas request,UserPrincipal user){
        Driver d=get(id);authorize(d,user);return modify(Criteria.where("id").is(id),new Update().set("serviceAreas",request.serviceAreas()));
    }
    public Vehicle addVehicle(VehicleRequest r,UserPrincipal user){
        owned(r.driverId(),user);
        return vehicles.insert(new Vehicle(r.driverId(),r.plateNumber(),r.make(),r.model(),r.year(),r.color(),r.type(),r.seatCapacity()));
    }
    public List<Vehicle> vehicles(String driverId,UserPrincipal user){owned(driverId,user);return vehicles.findByDriverId(driverId);}
    public List<Driver> available(){
        return drivers.findByStatusAndReservedRideIdIsNull(DriverStatus.AVAILABLE).stream()
                .filter(d->vehicles.existsByDriverIdAndActiveTrue(d.getId())).toList();
    }
    public Driver reserve(String id,String rideId){
        if(vehicles.existsByDriverIdAndActiveTrue(id)){
            Driver reserved=modify(Criteria.where("id").is(id).and("status").is(DriverStatus.AVAILABLE)
                    .and("reservedRideId").is(null),new Update().set("reservedRideId",rideId).set("status",DriverStatus.BUSY));
            if(reserved!=null)return reserved;
        }
        Driver current=get(id);
        if(rideId.equals(current.getReservedRideId()))return current;
        throw conflict("Driver unavailable");
    }
    public Driver release(String id,String rideId){
        // Match both reservation and status: concurrent OFFLINE updates cannot be overwritten.
        for(DriverStatus status:List.of(DriverStatus.BUSY,DriverStatus.OFFLINE)){
            Driver released=modify(Criteria.where("id").is(id).and("reservedRideId").is(rideId)
                    .and("status").is(status),new Update().unset("reservedRideId")
                    .set("status",status==DriverStatus.BUSY?DriverStatus.AVAILABLE:DriverStatus.OFFLINE));
            if(released!=null)return released;
        }
        Driver current=get(id);
        if(current.getReservedRideId()==null)return current;
        throw conflict("Reservation belongs to another ride");
    }
    private Driver modify(Criteria condition,Update update){
        return mongo.findAndModify(Query.query(condition),update,
                FindAndModifyOptions.options().returnNew(true),Driver.class);
    }
    private ResponseStatusException conflict(String m){return new ResponseStatusException(HttpStatus.CONFLICT,m);}
    private ResponseStatusException missing(String m){return new ResponseStatusException(HttpStatus.NOT_FOUND,m);}
    private ResponseStatusException forbidden(){return new ResponseStatusException(HttpStatus.FORBIDDEN,"Access denied");}
}
