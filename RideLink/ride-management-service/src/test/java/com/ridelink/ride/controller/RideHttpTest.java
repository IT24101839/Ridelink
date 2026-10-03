package com.ridelink.ride.controller;

import com.ridelink.ride.client.*;
import com.ridelink.ride.client.DriverServiceClient.DriverProfile;
import com.ridelink.ride.config.SecurityConfig;
import com.ridelink.ride.exception.GlobalExceptionHandler;
import com.ridelink.ride.model.*;
import com.ridelink.ride.repository.RideRepository;
import com.ridelink.ride.service.RideService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {RideController.class, InternalRideController.class, HealthController.class},
        properties = {"ride.jwt-secret=01234567890123456789012345678901", "ride.internal-token=test-internal-token"})
@Import({SecurityConfig.class, RideService.class, GlobalExceptionHandler.class})
class RideHttpTest {
    private static final String SECRET = "01234567890123456789012345678901";
    private static final String CREATE = """
            {"pickup":{"address":"Colombo","latitude":6.9,"longitude":79.8},
             "destination":{"address":"Kandy","latitude":7.2,"longitude":80.6}}
            """;

    @Autowired MockMvc mvc;
    @MockBean RideRepository repository;
    @MockBean DriverServiceClient drivers;
    @MockBean FareServiceClient fares;

    @BeforeEach void setup() {
        when(repository.save(any(Ride.class))).thenAnswer(i -> i.getArgument(0));
    }

    private String token(String userId, String role) {
        return token(userId, role, SECRET, Instant.now().plusSeconds(600));
    }

    private String token(String userId, String role, String secret, Instant expiry) {
        return "Bearer " + Jwts.builder().subject("email@example.com")
                .claim("userId", userId).claim("role", role)
                .expiration(Date.from(expiry))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8))).compact();
    }

    private void stored(RideStatus status) {
        Instant now = Instant.now().minusSeconds(300);
        when(repository.findById("ride-1")).thenReturn(Optional.of(new Ride(
                "ride-1", "rider-account", "driver-profile", status,
                new Location("Colombo", 6.9, 79.8), new Location("Kandy", 7.2, 80.6),
                new FareDetails("fare-1", BigDecimal.TEN, "LKR"), null,
                now, now, now, now, now, null, null, "driver-profile", 1L)));
    }

    @Test void healthRemainsPublic() throws Exception {
        mvc.perform(get("/api/ride/health")).andExpect(status().isOk())
                .andExpect(content().string("Ride Management Service is running"));
    }

    @Test void missingJwtIs401() throws Exception {
        mvc.perform(get("/api/rides/ride-1")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.timestamp").exists());
        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Bearer invalid", "Basic abc", "Bearer "})
    void invalidJwtIs401(String header) throws Exception {
        mvc.perform(get("/api/rides/ride-1").header("Authorization", header))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(repository);
    }

    @Test void wrongSignatureIs401() throws Exception {
        mvc.perform(get("/api/rides/ride-1").header("Authorization",
                token("rider-account", "RIDER", "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx", Instant.now().plusSeconds(600))))
                .andExpect(status().isUnauthorized());
    }

    @Test void expiredJwtIs401() throws Exception {
        mvc.perform(get("/api/rides/ride-1").header("Authorization",
                token("rider-account", "RIDER", SECRET, Instant.now().minusSeconds(60))))
                .andExpect(status().isUnauthorized());
    }

    @Test void jwtWithoutUserIdIs401() throws Exception {
        mvc.perform(get("/api/rides/ride-1").header("Authorization", token(null, "RIDER")))
                .andExpect(status().isUnauthorized());
    }

    @Test void jwtWithoutExpirationIs401() throws Exception {
        String jwt = Jwts.builder().claim("userId", "rider-account").claim("role", "RIDER")
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
        mvc.perform(get("/api/rides/ride-1").header("Authorization", "Bearer " + jwt))
                .andExpect(status().isUnauthorized());
    }

    @Test void unknownRoleIs401() throws Exception {
        mvc.perform(get("/api/rides/ride-1").header("Authorization", token("rider-account", "SERVICE")))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"RIDER", "PASSENGER", "ROLE_PASSENGER"})
    void riderAliasesCreateUsingJwtUserId(String role) throws Exception {
        mvc.perform(post("/api/rides").header("Authorization", token("rider-account", role))
                .contentType(MediaType.APPLICATION_JSON).content(CREATE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.riderId").value("rider-account"))
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.version").doesNotExist())
                .andExpect(jsonPath("$.activeDriverId").doesNotExist());
    }

    @Test void wrongRoleIs403() throws Exception {
        mvc.perform(post("/api/rides").header("Authorization", token("driver-account", "DRIVER"))
                .contentType(MediaType.APPLICATION_JSON).content(CREATE))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
        verify(repository, never()).save(any());
    }

    @Test void riderOwnershipIs403() throws Exception {
        stored(RideStatus.REQUESTED);
        mvc.perform(get("/api/rides/ride-1").header("Authorization", token("other", "RIDER")))
                .andExpect(status().isForbidden());
    }

    @Test void wrongDriverIs403() throws Exception {
        stored(RideStatus.ASSIGNED);
        when(drivers.byAccountId("driver-account"))
                .thenReturn(new DriverProfile("different-profile", "driver-account", "AVAILABLE"));
        mvc.perform(post("/api/rides/ride-1/accept")
                .header("Authorization", token("driver-account", "DRIVER")))
                .andExpect(status().isForbidden());
    }

    @Test void adminCanReadRide() throws Exception {
        stored(RideStatus.REQUESTED);
        mvc.perform(get("/api/rides/ride-1").header("Authorization", token("admin", "ADMIN")))
                .andExpect(status().isOk());
    }

    @Test void missingRideIs404() throws Exception {
        mvc.perform(get("/api/rides/absent").header("Authorization", token("rider-account", "RIDER")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"pickup\":{},\"destination\":{}}",
            "{\"pickup\":{\"address\":\"A\",\"latitude\":91,\"longitude\":0},\"destination\":{\"address\":\"B\",\"latitude\":0,\"longitude\":0}}",
            "{\"pickup\":{\"address\":\"A\",\"latitude\":0,\"longitude\":181},\"destination\":{\"address\":\"B\",\"latitude\":0,\"longitude\":0}}",
            "{\"pickup\":{\"address\":\"A\",\"latitude\":0,\"longitude\":0},\"destination\":{\"address\":\"\",\"latitude\":-91,\"longitude\":-181}}"
    })
    void invalidLocationsAre400(String body) throws Exception {
        mvc.perform(post("/api/rides").header("Authorization", token("rider-account", "RIDER"))
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors").isMap());
        verify(repository, never()).save(any());
    }

    @Test void malformedJsonIs400() throws Exception {
        mvc.perform(post("/api/rides").header("Authorization", token("rider-account", "RIDER"))
                .contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"distanceKm\":0}", "{\"distanceKm\":-1}"})
    void invalidDistanceIs400(String body) throws Exception {
        mvc.perform(post("/api/rides/ride-1/complete").header("Authorization", token("driver-account", "DRIVER"))
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(fares);
    }

    @Test void blankAssignmentIs400() throws Exception {
        mvc.perform(post("/api/rides/ride-1/assign").header("Authorization", token("admin", "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"driverId\":\" \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test void invalidHistoryPaginationIs400() throws Exception {
        mvc.perform(get("/api/rides/rider/history?page=-1&size=101")
                .header("Authorization", token("rider-account", "RIDER")))
                .andExpect(status().isBadRequest());
    }

    @Test void terminalCancellationIs409() throws Exception {
        stored(RideStatus.COMPLETED);
        mvc.perform(post("/api/rides/ride-1/cancel").header("Authorization", token("rider-account", "RIDER"))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409));
    }

    @Test void optimisticConflictIs409() throws Exception {
        when(repository.save(any())).thenThrow(new OptimisticLockingFailureException("race"));
        mvc.perform(post("/api/rides").header("Authorization", token("rider-account", "RIDER"))
                .contentType(MediaType.APPLICATION_JSON).content(CREATE))
                .andExpect(status().isConflict());
    }

    @Test void duplicateActiveDriverIs409() throws Exception {
        when(repository.save(any())).thenThrow(new DuplicateKeyException("busy"));
        mvc.perform(post("/api/rides").header("Authorization", token("rider-account", "RIDER"))
                .contentType(MediaType.APPLICATION_JSON).content(CREATE))
                .andExpect(status().isConflict());
    }

    @Test void databaseFailureIs503AndDoesNotLeakDetails() throws Exception {
        when(repository.findById(anyString())).thenThrow(new DataAccessResourceFailureException("secret-host"));
        mvc.perform(get("/api/rides/ride-1").header("Authorization", token("rider-account", "RIDER")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Ride database unavailable"));
    }

    @Test void internalContextRequiresServiceTokenEvenWithAdminJwt() throws Exception {
        mvc.perform(get("/api/internal/rides/ride-1/payment-context")
                .header("Authorization", token("admin", "ADMIN"))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/internal/rides/ride-1/payment-context")
                .header("X-Service-Token", "wrong")).andExpect(status().isUnauthorized());
    }

    @Test void serviceTokenCannotAccessUserEndpoints() throws Exception {
        mvc.perform(get("/api/rides/ride-1").header("X-Service-Token", "test-internal-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test void completedContextIsReturnedToServiceOnly() throws Exception {
        stored(RideStatus.COMPLETED);
        mvc.perform(get("/api/internal/rides/ride-1/payment-context")
                .header("X-Service-Token", "test-internal-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.riderId").value("rider-account"))
                .andExpect(jsonPath("$.fare.fareId").value("fare-1"))
                .andExpect(jsonPath("$.fare.totalAmount").value(10));
    }
}
