package com.ridelink.ride.controller;

import com.ridelink.ride.dto.PaymentContext;
import com.ridelink.ride.service.RideService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/internal/rides")
@SecurityRequirement(name = "serviceToken")
public class InternalRideController {
    private final RideService service;

    public InternalRideController(RideService service) {
        this.service = service;
    }

    @GetMapping("/{id}/payment-context")
    @PreAuthorize("hasRole('SERVICE')")
    public PaymentContext paymentContext(@PathVariable String id) {
        return service.paymentContext(id);
    }
}
