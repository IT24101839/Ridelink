package com.ridelink.drivervehicle.repository;
import com.ridelink.drivervehicle.model.*;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.*;
public interface DriverRepository extends MongoRepository<Driver,String> {
    Optional<Driver> findByAccountId(String accountId);
    List<Driver> findByStatusAndReservedRideIdIsNull(DriverStatus status);
}
