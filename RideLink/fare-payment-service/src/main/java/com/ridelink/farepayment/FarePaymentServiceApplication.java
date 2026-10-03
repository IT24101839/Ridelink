package com.ridelink.farepayment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude = org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class)
public class FarePaymentServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(FarePaymentServiceApplication.class, args);
    }
}
