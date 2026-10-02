package com.ridelink.ride.client;

import com.ridelink.ride.exception.ApiException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class ServiceClientsTest {
    private MockRestServiceServer server;
    private DriverServiceClient drivers;
    private FareServiceClient fares;

    @BeforeEach void setup() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        drivers = new DriverServiceClient(builder, "http://driver.test", "driver-token");
        fares = new FareServiceClient(builder, "http://fare.test", "fare-token");
    }

    private void hasStatus(Runnable action, int status) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class,
                ex -> assertThat(ex.status().value()).isEqualTo(status));
        server.verify();
    }

    @Test void mapsAccountToDriverProfileWithServiceToken() {
        server.expect(requestTo("http://driver.test/api/internal/drivers/account/account-123"))
                .andExpect(header("X-Service-Token", "driver-token"))
                .andRespond(withSuccess("""
                        {"id":"profile-456","accountId":"account-123","status":"BUSY"}
                        """, MediaType.APPLICATION_JSON));
        var driver = drivers.byAccountId("account-123");
        assertThat(driver.id()).isEqualTo("profile-456");
        assertThat(driver.accountId()).isEqualTo("account-123");
        server.verify();
    }

    @Test void looksUpDriverProfileForAssignment() {
        server.expect(requestTo("http://driver.test/api/internal/drivers/profile-1"))
                .andExpect(header("X-Service-Token", "driver-token"))
                .andRespond(withSuccess("""
                        {"id":"profile-1","accountId":"account-1","status":"AVAILABLE"}
                        """, MediaType.APPLICATION_JSON));
        assertThat(drivers.byId("profile-1").available()).isTrue();
        server.verify();
    }

    @Test void availableLookupFiltersIneligibleStatuses() {
        server.expect(requestTo("http://driver.test/api/internal/drivers/available"))
                .andRespond(withSuccess("""
                        [{"id":"p1","accountId":"a1","status":"AVAILABLE"},
                         {"id":"p2","accountId":"a2","status":"BUSY"}]
                        """, MediaType.APPLICATION_JSON));
        assertThat(drivers.availableDrivers()).extracting(DriverServiceClient.DriverProfile::id)
                .containsExactly("p1");
        server.verify();
    }

    @Test void missingDriverIs404() {
        server.expect(requestTo("http://driver.test/api/internal/drivers/missing"))
                .andRespond(withResourceNotFound());
        hasStatus(() -> drivers.byId("missing"), 404);
    }

    @Test void missingAccountProfileIs404() {
        server.expect(requestTo("http://driver.test/api/internal/drivers/account/missing"))
                .andRespond(withResourceNotFound());
        hasStatus(() -> drivers.byAccountId("missing"), 404);
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 409, 500, 503})
    void driverFailuresAre503(int status) {
        server.expect(requestTo("http://driver.test/api/internal/drivers/p1"))
                .andRespond(withStatus(HttpStatusCode.valueOf(status)));
        hasStatus(() -> drivers.byId("p1"), 503);
    }

    @Test void missingAvailableRouteIs503() {
        server.expect(requestTo("http://driver.test/api/internal/drivers/available"))
                .andRespond(withResourceNotFound());
        hasStatus(() -> drivers.availableDrivers(), 503);
    }

    @Test void driverTimeoutIs503() {
        server.expect(requestTo("http://driver.test/api/internal/drivers/p1"))
                .andRespond(withException(new SocketTimeoutException("timeout")));
        hasStatus(() -> drivers.byId("p1"), 503);
    }

    @Test void wrongAccountMappingFailsClosed() {
        server.expect(requestTo("http://driver.test/api/internal/drivers/account/account-1"))
                .andRespond(withSuccess("""
                        {"id":"profile-1","accountId":"other","status":"AVAILABLE"}
                        """, MediaType.APPLICATION_JSON));
        hasStatus(() -> drivers.byAccountId("account-1"), 503);
    }

    @Test void wrongProfileMappingFailsClosed() {
        server.expect(requestTo("http://driver.test/api/internal/drivers/p1"))
                .andRespond(withSuccess("""
                        {"id":"other","accountId":"a1","status":"AVAILABLE"}
                        """, MediaType.APPLICATION_JSON));
        hasStatus(() -> drivers.byId("p1"), 503);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "{}", "invalid-json",
            "{\"id\":\"p1\",\"accountId\":\"\",\"status\":\"AVAILABLE\"}"})
    void malformedDriverIs503(String response) {
        server.expect(requestTo("http://driver.test/api/internal/drivers/p1"))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        hasStatus(() -> drivers.byId("p1"), 503);
    }

    @Test void missingDriverTokenDisablesRequests() {
        drivers = new DriverServiceClient(RestClient.builder(), "http://driver.test", "");
        hasStatus(() -> drivers.byId("p1"), 503);
    }

    @Test void fareRequestCarriesTrustedDistanceDurationAndIdempotencyKey() {
        server.expect(requestTo("http://fare.test/api/fare-payment/fares/calculate"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Service-Token", "fare-token"))
                .andExpect(header("Idempotency-Key", "ride-completion-ride-1"))
                .andExpect(content().json("""
                        {"rideId":"ride-1","distanceKm":10,"durationMinutes":5}
                        """))
                .andRespond(withSuccess("""
                        {"id":"fare-1","rideId":"ride-1","totalAmount":950.00,"currency":"LKR"}
                        """, MediaType.APPLICATION_JSON));
        var fare = fares.calculate("ride-1", BigDecimal.TEN, 5);
        assertThat(fare.fareId()).isEqualTo("fare-1");
        assertThat(fare.totalAmount()).isEqualByComparingTo("950");
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 404, 409, 500, 503})
    void fareFailuresAre503(int status) {
        server.expect(requestTo("http://fare.test/api/fare-payment/fares/calculate"))
                .andRespond(withStatus(HttpStatusCode.valueOf(status)));
        hasStatus(() -> fares.calculate("ride-1", BigDecimal.ONE, 1), 503);
    }

    @Test void fareTimeoutIs503() {
        server.expect(requestTo("http://fare.test/api/fare-payment/fares/calculate"))
                .andRespond(withException(new SocketTimeoutException("timeout")));
        hasStatus(() -> fares.calculate("ride-1", BigDecimal.ONE, 1), 503);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "null", "{}", "not-json",
            "{\"id\":\"f1\",\"rideId\":\"other\",\"totalAmount\":10,\"currency\":\"LKR\"}",
            "{\"id\":\"f1\",\"rideId\":\"ride-1\",\"totalAmount\":-1,\"currency\":\"LKR\"}",
            "{\"id\":\"f1\",\"rideId\":\"ride-1\",\"totalAmount\":10,\"currency\":\"\"}"
    })
    void malformedFareIs503(String response) {
        server.expect(requestTo("http://fare.test/api/fare-payment/fares/calculate"))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        hasStatus(() -> fares.calculate("ride-1", BigDecimal.ONE, 1), 503);
    }

    @Test void missingFareTokenDisablesRequests() {
        fares = new FareServiceClient(RestClient.builder(), "http://fare.test", "");
        hasStatus(() -> fares.calculate("ride-1", BigDecimal.ONE, 1), 503);
    }
}
