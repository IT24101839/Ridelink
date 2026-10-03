package com.ridelink.ride.client;

import com.ridelink.ride.exception.ApiException;
import com.ridelink.ride.model.FareDetails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;

import java.math.BigDecimal;

@Component
public class FareServiceClient {
    private final RestClient client;
    private final String token;

    public FareServiceClient(RestClient.Builder builder,
            @Value("${ride.fare.base-url}") String baseUrl,
            @Value("${ride.fare.token:}") String token) {
        this.client = builder.clone().baseUrl(baseUrl).build();
        this.token = token;
    }

    public record FareRequest(String rideId, BigDecimal distanceKm, long durationMinutes) {
    }

    public record FareResponse(String id, String rideId, BigDecimal totalAmount, String currency) {
    }

    public FareDetails calculate(String rideId, BigDecimal distanceKm, long durationMinutes) {
        if (token.isBlank()) {
            throw unavailable();
        }
        try {
            FareResponse fare = client.post().uri("/api/fare-payment/fares/calculate")
                    .header("X-Service-Token", token)
                    // Required downstream contract: repeated completion attempts return the same fare.
                    .header("Idempotency-Key", "ride-completion-" + rideId)
                    .body(new FareRequest(rideId, distanceKm, durationMinutes))
                    .retrieve().body(FareResponse.class);
            if (fare == null || fare.id() == null || fare.id().isBlank()
                    || !rideId.equals(fare.rideId()) || fare.totalAmount() == null
                    || fare.totalAmount().signum() < 0
                    || fare.currency() == null || !fare.currency().matches("[A-Z]{3}")) {
                throw unavailable();
            }
            return new FareDetails(fare.id(), fare.totalAmount(), fare.currency());
        } catch (RestClientException ex) {
            throw unavailable();
        }
    }

    private ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Fare Service unavailable or incompatible");
    }
}
