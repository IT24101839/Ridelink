package com.ridelink.ride.controller;

import com.ridelink.ride.client.DriverServiceClient.DriverProfile;
import com.ridelink.ride.dto.*;
import com.ridelink.ride.model.Ride;
import com.ridelink.ride.security.UserPrincipal;
import com.ridelink.ride.service.RideService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rides")
@SecurityRequirement(name = "bearerAuth")
public class RideController {
    private final RideService service;

    public RideController(RideService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('RIDER')")
    public Ride create(@AuthenticationPrincipal UserPrincipal user, @Valid @RequestBody CreateRideRequest request) {
        return service.create(user, request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('RIDER', 'DRIVER', 'ADMIN')")
    public Ride get(@PathVariable String id, @AuthenticationPrincipal UserPrincipal user) {
        return service.get(id, user);
    }

    @GetMapping("/rider/history")
    @PreAuthorize("hasRole('RIDER')")
    public Page<Ride> riderHistory(@AuthenticationPrincipal UserPrincipal user,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.riderHistory(user, page, size);
    }

    @GetMapping("/driver/history")
    @PreAuthorize("hasRole('DRIVER')")
    public Page<Ride> driverHistory(@AuthenticationPrincipal UserPrincipal user,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.driverHistory(user, page, size);
    }

    @GetMapping("/available-drivers")
    @PreAuthorize("hasAnyRole('RIDER', 'ADMIN')")
    public List<DriverProfile> availableDrivers(@AuthenticationPrincipal UserPrincipal user) {
        return service.availableDrivers(user);
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasRole('ADMIN')")
    public Ride assign(@PathVariable String id, @Valid @RequestBody AssignDriverRequest request,
                       @AuthenticationPrincipal UserPrincipal user) {
        return service.assign(id, request.driverId(), user);
    }

    @PostMapping("/{id}/accept")
    @PreAuthorize("hasRole('DRIVER')")
    public Ride accept(@PathVariable String id, @AuthenticationPrincipal UserPrincipal user) {
        return service.accept(id, user);
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasRole('DRIVER')")
    public Ride start(@PathVariable String id, @AuthenticationPrincipal UserPrincipal user) {
        return service.start(id, user);
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasRole('DRIVER')")
    public Ride complete(@PathVariable String id, @Valid @RequestBody CompleteRideRequest request,
                         @AuthenticationPrincipal UserPrincipal user) {
        return service.complete(id, request, user);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('RIDER', 'ADMIN')")
    public Ride cancel(@PathVariable String id, @Valid @RequestBody CancelRideRequest request,
                       @AuthenticationPrincipal UserPrincipal user) {
        return service.cancel(id, request, user);
    }
}
