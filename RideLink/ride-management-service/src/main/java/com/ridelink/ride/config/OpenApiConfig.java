package com.ridelink.ride.config;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
@SecurityScheme(name = "serviceToken", type = SecuritySchemeType.APIKEY,
        in = SecuritySchemeIn.HEADER, paramName = "X-Service-Token")
public class OpenApiConfig {
}
