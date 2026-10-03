package com.ridelink.ride.repository;

import com.ridelink.ride.model.Ride;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface RideRepository extends MongoRepository<Ride, String> {
    Page<Ride> findByRiderIdOrderByCreatedAtDesc(String riderId, Pageable pageable);
    Page<Ride> findByDriverIdOrderByCreatedAtDesc(String driverId, Pageable pageable);
    java.util.List<Ride> findByStatusAndActiveDriverIdIsNotNull(com.ridelink.ride.model.RideStatus status, Pageable pageable);
    java.util.List<Ride> findByStatusAndCompletionRequestedAtIsNotNull(com.ridelink.ride.model.RideStatus status, Pageable pageable);
    java.util.List<Ride> findByReleaseDriverIdIsNotNull(Pageable pageable);
    boolean existsByActiveDriverId(String driverId);
}
