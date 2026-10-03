package com.ridelink.drivervehicle.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info = @Info(
        title = "RideLink Driver & Vehicle Service API",
        version = "1.0.0",
        description = "REST API for basic driver and vehicle management."
))
public class OpenApiConfig {
}
