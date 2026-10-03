package com.ridelink.drivervehicle.repository;

import com.ridelink.drivervehicle.entity.Driver;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface DriverRepository extends MongoRepository<Driver, String> {
    java.util.Optional<Driver> findByEmail(String email);
}
