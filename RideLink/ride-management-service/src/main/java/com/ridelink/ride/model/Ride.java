package com.ridelink.ride.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;

@Document(collection = "rides")
@CompoundIndexes({
    @CompoundIndex(name = "rider_history", def = "{'riderId': 1, 'createdAt': -1}"),
    @CompoundIndex(name = "driver_history", def = "{'driverId': 1, 'createdAt': -1}")
})
public record Ride(
        @Id String id,
        String riderId,
        String driverId,
        RideStatus status,
        Location pickup,
        Location destination,
        FareDetails fare,
        BigDecimal distanceKm,
        Instant createdAt,
        Instant assignedAt,
        Instant acceptedAt,
        Instant startedAt,
        Instant completedAt,
        Instant cancelledAt,
        String cancellationReason,
        @JsonIgnore
        @Indexed(name = "one_active_ride_per_driver", unique = true,
                 partialFilter = "{'activeDriverId': {'$type': 'string'}}")
        String activeDriverId,
        @Version @JsonIgnore Long version) {

    public static Ride requested(String riderId, Location pickup, Location destination, Instant now) {
        return new Ride(null, riderId, null, RideStatus.REQUESTED, pickup, destination,
                null, null, now, null, null, null, null, null, null, null, null);
    }

    public Ride assign(String profileId, Instant now) {
        return new Ride(id, riderId, profileId, RideStatus.ASSIGNED, pickup, destination,
                fare, distanceKm, createdAt, now, acceptedAt, startedAt, completedAt,
                cancelledAt, cancellationReason, profileId, version);
    }

    public Ride transition(RideStatus next, Instant now, BigDecimal distance,
                           FareDetails calculatedFare, String reason) {
        boolean terminal = next == RideStatus.COMPLETED || next == RideStatus.CANCELLED;
        return new Ride(id, riderId, driverId, next, pickup, destination,
                calculatedFare == null ? fare : calculatedFare,
                distance == null ? distanceKm : distance, createdAt, assignedAt,
                next == RideStatus.ACCEPTED ? now : acceptedAt,
                next == RideStatus.IN_PROGRESS ? now : startedAt,
                next == RideStatus.COMPLETED ? now : completedAt,
                next == RideStatus.CANCELLED ? now : cancelledAt,
                next == RideStatus.CANCELLED ? reason : cancellationReason,
                terminal ? null : activeDriverId, version);
    }
}
