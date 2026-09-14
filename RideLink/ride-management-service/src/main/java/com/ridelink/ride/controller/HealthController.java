package com.ridelink.ride.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ride")
public class HealthController {

    @GetMapping("/health")
    public String health() {
        return "Ride Management Service is running";
    }
}
