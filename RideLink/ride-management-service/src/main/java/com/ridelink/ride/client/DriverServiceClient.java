package com.ridelink.ride.client;

import com.ridelink.ride.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;

import java.util.Arrays;
import java.util.List;

@Component
public class DriverServiceClient {
    private final RestClient client;
    private final String token;

    public DriverServiceClient(RestClient.Builder builder,
            @Value("${ride.driver.base-url}") String baseUrl,
            @Value("${ride.driver.token:}") String token) {
        this.client = builder.clone().baseUrl(baseUrl).build();
        this.token = token;
    }

    public record DriverProfile(String id, String accountId, String status) {
        public boolean available() {
            return "AVAILABLE".equals(status);
        }
    }

    public DriverProfile byId(String driverId) {
        DriverProfile driver = get("/api/internal/drivers/{id}", DriverProfile.class, driverId);
        validate(driver);
        if (!driverId.equals(driver.id())) {
            throw unavailable();
        }
        return driver;
    }

    public DriverProfile byAccountId(String accountId) {
        DriverProfile driver = get("/api/internal/drivers/account/{id}", DriverProfile.class, accountId);
        validate(driver);
        if (!accountId.equals(driver.accountId())) {
            throw unavailable();
        }
        return driver;
    }

    public List<DriverProfile> availableDrivers() {
        DriverProfile[] drivers = get("/api/internal/drivers/available", DriverProfile[].class);
        if (drivers == null) {
            throw unavailable();
        }
        Arrays.stream(drivers).forEach(this::validate);
        return Arrays.stream(drivers).filter(DriverProfile::available).toList();
    }

    private void validate(DriverProfile driver) {
        if (driver == null || driver.id() == null || driver.id().isBlank()
                || driver.accountId() == null || driver.accountId().isBlank()
                || driver.status() == null || driver.status().isBlank()) {
            throw unavailable();
        }
    }

    private <T> T get(String path, Class<T> type, Object... variables) {
        if (token.isBlank()) {
            throw unavailable();
        }
        try {
            return client.get().uri(path, variables).header("X-Service-Token", token)
                    .retrieve().body(type);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404 && variables.length > 0) {
                throw new ApiException(HttpStatus.NOT_FOUND, "Driver not found");
            }
            throw unavailable();
        } catch (RestClientException ex) {
            throw unavailable();
        }
    }

    private ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Driver Service unavailable or incompatible");
    }
}
