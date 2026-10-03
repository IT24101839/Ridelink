package com.ridelink.drivervehicle;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude = org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class)
public class DriverVehicleServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(DriverVehicleServiceApplication.class, args);
    }
}
