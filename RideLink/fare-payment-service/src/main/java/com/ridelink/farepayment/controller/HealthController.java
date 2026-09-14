package com.ridelink.farepayment.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/fare-payment")
public class HealthController {

    @GetMapping("/health")
    public String health() {
        return "Fare Payment Service is running";
    }
}
