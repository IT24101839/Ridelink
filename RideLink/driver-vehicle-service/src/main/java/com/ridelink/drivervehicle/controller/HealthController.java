package com.ridelink.drivervehicle.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/driver-vehicle")
public class HealthController {

    @GetMapping("/health")
    public String health() {
        return "Driver Vehicle Service is running";
    }
}
