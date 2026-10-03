package com.ridelink.drivervehicle.repository;

import com.ridelink.drivervehicle.entity.Vehicle;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface VehicleRepository extends MongoRepository<Vehicle, String> {
    java.util.Optional<Vehicle> findByRegistrationNumber(String registrationNumber);

    void deleteAllByDriverId(String driverId);
}
