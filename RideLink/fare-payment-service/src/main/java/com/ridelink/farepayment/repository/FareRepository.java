package com.ridelink.farepayment.repository;

import com.ridelink.farepayment.model.Fare;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface FareRepository extends MongoRepository<Fare, String> {
    Optional<Fare> findByRideId(Long rideId);
}
