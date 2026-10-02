package com.ridelink.ride.service;

import com.ridelink.ride.client.DriverServiceClient;
import com.ridelink.ride.client.DriverServiceClient.DriverProfile;
import com.ridelink.ride.client.FareServiceClient;
import com.ridelink.ride.dto.*;
import com.ridelink.ride.exception.ApiException;
import com.ridelink.ride.model.*;
import com.ridelink.ride.repository.RideRepository;
import com.ridelink.ride.security.UserPrincipal;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.List;

@Service
public class RideService {
    private final RideRepository rides;
    private final DriverServiceClient drivers;
    private final FareServiceClient fares;

    public RideService(RideRepository rides, DriverServiceClient drivers, FareServiceClient fares) {
        this.rides = rides;
        this.drivers = drivers;
        this.fares = fares;
    }

    public Ride create(UserPrincipal user, CreateRideRequest request) {
        requireRole(user, "RIDER");
        return rides.save(Ride.requested(user.userId(), request.pickup(), request.destination(), Instant.now()));
    }

    public Ride get(String id, UserPrincipal user) {
        Ride ride = find(id);
        if (user.isAdmin() || ("RIDER".equals(user.role()) && ride.riderId().equals(user.userId()))) {
            return ride;
        }
        requireAssignedDriver(ride, user);
        return ride;
    }

    public Page<Ride> riderHistory(UserPrincipal user, int page, int size) {
        requireRole(user, "RIDER");
        return rides.findByRiderIdOrderByCreatedAtDesc(user.userId(), PageRequest.of(page, size));
    }

    public Page<Ride> driverHistory(UserPrincipal user, int page, int size) {
        requireRole(user, "DRIVER");
        String driverId = drivers.byAccountId(user.userId()).id();
        return rides.findByDriverIdOrderByCreatedAtDesc(driverId, PageRequest.of(page, size));
    }

    public List<DriverProfile> availableDrivers(UserPrincipal user) {
        requireRiderOrAdmin(user);
        return drivers.availableDrivers().stream()
                .filter(driver -> !rides.existsByActiveDriverId(driver.id())).toList();
    }

    public Ride assign(String id, String driverId, UserPrincipal user) {
        requireRole(user, "ADMIN");
        Ride ride = find(id);
        requireState(ride, RideStatus.REQUESTED);
        DriverProfile driver = drivers.byId(driverId);
        if (!driver.available() || rides.existsByActiveDriverId(driver.id())) {
            throw conflict("Driver is unavailable");
        }
        return rides.save(ride.assign(driver.id(), Instant.now()));
    }

    public Ride accept(String id, UserPrincipal user) {
        Ride ride = find(id);
        requireAssignedDriver(ride, user);
        requireState(ride, RideStatus.ASSIGNED);
        return rides.save(ride.transition(RideStatus.ACCEPTED, Instant.now(), null, null, null));
    }

    public Ride start(String id, UserPrincipal user) {
        Ride ride = find(id);
        requireAssignedDriver(ride, user);
        requireState(ride, RideStatus.ACCEPTED);
        return rides.save(ride.transition(RideStatus.IN_PROGRESS, Instant.now(), null, null, null));
    }

    public Ride complete(String id, CompleteRideRequest request, UserPrincipal user) {
        Ride ride = find(id);
        requireAssignedDriver(ride, user);
        requireState(ride, RideStatus.IN_PROGRESS);
        if (request.distanceKm() == null || request.distanceKm().signum() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Completion distance must be greater than zero");
        }
        Instant now = Instant.now();
        long durationMinutes = Math.max(0, Duration.between(ride.startedAt(), now).toMinutes());
        // Leave IN_PROGRESS intact on dependency failure. @Version protects the final write.
        FareDetails fare = fares.calculate(id, request.distanceKm(), durationMinutes);
        return rides.save(ride.transition(RideStatus.COMPLETED, now, request.distanceKm(), fare, null));
    }

    public Ride cancel(String id, CancelRideRequest request, UserPrincipal user) {
        requireRiderOrAdmin(user);
        Ride ride = find(id);
        if (!user.isAdmin() && !ride.riderId().equals(user.userId())) {
            throw forbidden();
        }
        if (ride.status() == RideStatus.COMPLETED || ride.status() == RideStatus.CANCELLED) {
            throw conflict("Terminal rides cannot be cancelled");
        }
        return rides.save(ride.transition(RideStatus.CANCELLED, Instant.now(), null, null, request.reason()));
    }

    public PaymentContext paymentContext(String id) {
        Ride ride = find(id);
        requireState(ride, RideStatus.COMPLETED);
        return new PaymentContext(ride.id(), ride.riderId(), ride.driverId(), ride.status(), ride.fare());
    }

    private Ride find(String id) {
        return rides.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Ride not found"));
    }

    private void requireAssignedDriver(Ride ride, UserPrincipal user) {
        requireRole(user, "DRIVER");
        if (ride.driverId() == null) {
            throw forbidden();
        }
        DriverProfile profile = drivers.byAccountId(user.userId());
        if (!ride.driverId().equals(profile.id())) {
            throw forbidden();
        }
    }

    private void requireState(Ride ride, RideStatus expected) {
        if (ride.status() != expected) {
            throw conflict("Ride must be " + expected + "; current status is " + ride.status());
        }
    }

    private void requireRole(UserPrincipal user, String role) {
        if (!role.equals(user.role())) {
            throw forbidden();
        }
    }

    private void requireRiderOrAdmin(UserPrincipal user) {
        if (!user.isAdmin() && !"RIDER".equals(user.role())) {
            throw forbidden();
        }
    }

    private ApiException forbidden() {
        return new ApiException(HttpStatus.FORBIDDEN, "Access denied");
    }

    private ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, message);
    }
}
