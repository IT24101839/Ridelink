package com.ridelink.account.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/account")
public class HealthController {

    @GetMapping("/health")
    public String health() {
        return "Account Service is running";
    }
}
