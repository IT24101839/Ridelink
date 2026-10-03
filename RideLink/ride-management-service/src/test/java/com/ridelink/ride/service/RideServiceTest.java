package com.ridelink.ride.service;

import com.ridelink.ride.client.*;
import com.ridelink.ride.client.DriverServiceClient.DriverProfile;
import com.ridelink.ride.dto.*;
import com.ridelink.ride.exception.ApiException;
import com.ridelink.ride.model.*;
import com.ridelink.ride.repository.RideRepository;
import com.ridelink.ride.security.UserPrincipal;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.*;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RideServiceTest {
    private RideRepository repository;
    private DriverServiceClient drivers;
    private FareServiceClient fares;
    private RideService service;
    private final UserPrincipal rider = new UserPrincipal("account-rider", "RIDER");
    private final UserPrincipal driver = new UserPrincipal("account-driver", "DRIVER");
    private final UserPrincipal admin = new UserPrincipal("admin", "ADMIN");
    private final Location location = new Location("Colombo", 6.9, 79.8);
    private final FareDetails fare = new FareDetails("fare-1", new BigDecimal("500.00"), "LKR");

    @BeforeEach
    void setup() {
        repository = mock(RideRepository.class);
        drivers = mock(DriverServiceClient.class);
        fares = mock(FareServiceClient.class);
        service = new RideService(repository, drivers, fares);
        when(repository.save(any(Ride.class))).thenAnswer(i -> i.getArgument(0));
    }

    private Ride ride(RideStatus status) {
        Instant time = Instant.now().minusSeconds(300);
        return new Ride("ride-1", rider.userId(), status == RideStatus.REQUESTED ? null : "profile-1",
                status, location, location, status == RideStatus.COMPLETED ? fare : null,
                null, time, time, time, time, null, null, null,
                status == RideStatus.REQUESTED || status == RideStatus.COMPLETED
                        || status == RideStatus.CANCELLED ? null : "profile-1", 3L);
    }

    private Ride stored(RideStatus status) {
        Ride ride = ride(status);
        when(repository.findById("ride-1")).thenReturn(Optional.of(ride));
        return ride;
    }

    private void mappedDriver() {
        when(drivers.byAccountId(driver.userId()))
                .thenReturn(new DriverProfile("profile-1", driver.userId(), "BUSY"));
    }

    private void hasStatus(Runnable action, int status) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class,
                ex -> assertThat(ex.status().value()).isEqualTo(status));
    }

    @Test void createUsesJwtIdentityAndRequestedState() {
        Ride created = service.create(rider, new CreateRideRequest(location, location));
        assertThat(created.riderId()).isEqualTo(rider.userId());
        assertThat(created.status()).isEqualTo(RideStatus.REQUESTED);
        assertThat(created.createdAt()).isNotNull();
        assertThat(created.driverId()).isNull();
        verifyNoInteractions(drivers, fares);
    }

    @Test void driverCannotCreate() {
        hasStatus(() -> service.create(driver, new CreateRideRequest(location, location)), 403);
        verify(repository, never()).save(any());
    }

    @Test void riderCanReadOwnRideAndAdminCanReadAnyRide() {
        Ride ride = stored(RideStatus.REQUESTED);
        assertThat(service.get("ride-1", rider)).isEqualTo(ride);
        assertThat(service.get("ride-1", admin)).isEqualTo(ride);
    }

    @Test void riderCannotReadAnotherRidersRide() {
        stored(RideStatus.REQUESTED);
        hasStatus(() -> service.get("ride-1", new UserPrincipal("other", "RIDER")), 403);
        verifyNoInteractions(drivers);
    }

    @Test void missingRideIs404() {
        hasStatus(() -> service.get("missing", rider), 404);
    }

    @Test void historiesUseAccountForRiderAndProfileForDriver() {
        mappedDriver();
        when(repository.findByRiderIdOrderByCreatedAtDesc(eq(rider.userId()), any()))
                .thenReturn(Page.empty());
        when(repository.findByDriverIdOrderByCreatedAtDesc(eq("profile-1"), any()))
                .thenReturn(Page.empty());
        assertThat(service.riderHistory(rider, 1, 10)).isEmpty();
        assertThat(service.driverHistory(driver, 0, 20)).isEmpty();
        verify(repository).findByRiderIdOrderByCreatedAtDesc(rider.userId(), PageRequest.of(1, 10));
        verify(repository).findByDriverIdOrderByCreatedAtDesc("profile-1", PageRequest.of(0, 20));
    }

    @Test void availableLookupExcludesLocallyAssignedDrivers() {
        when(drivers.availableDrivers()).thenReturn(List.of(
                new DriverProfile("free", "a", "AVAILABLE"),
                new DriverProfile("busy", "b", "AVAILABLE")));
        when(repository.existsByActiveDriverId("busy")).thenReturn(true);
        assertThat(service.availableDrivers(rider)).extracting(DriverProfile::id).containsExactly("free");
    }

    @Test void assignAvailableDriverStoresProfileIdAndActiveClaim() {
        stored(RideStatus.REQUESTED);
        when(drivers.byId("profile-1"))
                .thenReturn(new DriverProfile("profile-1", driver.userId(), "AVAILABLE"));
        Ride assigned = service.assign("ride-1", "profile-1", admin);
        assertThat(assigned.status()).isEqualTo(RideStatus.ASSIGNED);
        assertThat(assigned.driverId()).isEqualTo("profile-1").isNotEqualTo(driver.userId());
        assertThat(assigned.activeDriverId()).isEqualTo("profile-1");
        assertThat(assigned.assignedAt()).isNotNull();
        assertThat(assigned.version()).isEqualTo(3L);
    }

    @Test void unavailableDriverCannotBeAssigned() {
        stored(RideStatus.REQUESTED);
        when(drivers.byId("profile-1")).thenReturn(new DriverProfile("profile-1", "account", "BUSY"));
        hasStatus(() -> service.assign("ride-1", "profile-1", admin), 409);
        verify(repository, never()).save(any());
    }

    @Test void locallyBusyDriverCannotBeAssigned() {
        stored(RideStatus.REQUESTED);
        when(drivers.byId("profile-1")).thenReturn(new DriverProfile("profile-1", "account", "AVAILABLE"));
        when(repository.existsByActiveDriverId("profile-1")).thenReturn(true);
        hasStatus(() -> service.assign("ride-1", "profile-1", admin), 409);
    }

    @Test void riderCannotAssignDriver() {
        hasStatus(() -> service.assign("ride-1", "profile-1", rider), 403);
        verifyNoInteractions(drivers);
    }

    @ParameterizedTest
    @EnumSource(value = RideStatus.class, names = "REQUESTED", mode = EnumSource.Mode.EXCLUDE)
    void assignmentRequiresRequested(RideStatus status) {
        stored(status);
        hasStatus(() -> service.assign("ride-1", "profile-1", admin), 409);
        verifyNoInteractions(drivers);
    }

    @Test void acceptThenStartRequireMappedDriver() {
        stored(RideStatus.ASSIGNED);
        mappedDriver();
        Ride accepted = service.accept("ride-1", driver);
        assertThat(accepted.status()).isEqualTo(RideStatus.ACCEPTED);
        assertThat(accepted.acceptedAt()).isNotNull();
        when(repository.findById("ride-1")).thenReturn(Optional.of(accepted));
        Ride started = service.start("ride-1", driver);
        assertThat(started.status()).isEqualTo(RideStatus.IN_PROGRESS);
        assertThat(started.startedAt()).isNotNull();
        assertThat(started.activeDriverId()).isEqualTo("profile-1");
    }

    @Test void wrongDriverCannotReadAcceptStartOrComplete() {
        stored(RideStatus.ASSIGNED);
        when(drivers.byAccountId(driver.userId()))
                .thenReturn(new DriverProfile("other-profile", driver.userId(), "AVAILABLE"));
        hasStatus(() -> service.get("ride-1", driver), 403);
        hasStatus(() -> service.accept("ride-1", driver), 403);
        hasStatus(() -> service.start("ride-1", driver), 403);
        hasStatus(() -> service.complete("ride-1", new CompleteRideRequest(BigDecimal.ONE), driver), 403);
        verify(repository, never()).save(any());
        verifyNoInteractions(fares);
    }

    @Test void driverCanReadAssignedRide() {
        Ride ride = stored(RideStatus.ASSIGNED);
        mappedDriver();
        assertThat(service.get("ride-1", driver)).isEqualTo(ride);
    }

    @ParameterizedTest
    @EnumSource(value = RideStatus.class, names = {"ASSIGNED", "REQUESTED"}, mode = EnumSource.Mode.EXCLUDE)
    void invalidAcceptTransitions(RideStatus status) {
        stored(status);
        mappedDriver();
        hasStatus(() -> service.accept("ride-1", driver), 409);
    }

    @Test void unassignedRideCannotBeAccepted() {
        stored(RideStatus.REQUESTED);
        hasStatus(() -> service.accept("ride-1", driver), 403);
    }

    @ParameterizedTest
    @EnumSource(value = RideStatus.class, names = {"ACCEPTED", "REQUESTED"}, mode = EnumSource.Mode.EXCLUDE)
    void invalidStartTransitions(RideStatus status) {
        stored(status);
        mappedDriver();
        hasStatus(() -> service.start("ride-1", driver), 409);
    }

    @ParameterizedTest
    @EnumSource(value = RideStatus.class, names = {"IN_PROGRESS", "REQUESTED"}, mode = EnumSource.Mode.EXCLUDE)
    void invalidCompletionTransitions(RideStatus status) {
        stored(status);
        mappedDriver();
        hasStatus(() -> service.complete("ride-1", new CompleteRideRequest(BigDecimal.ONE), driver), 409);
        verifyNoInteractions(fares);
    }

    @Test void requestedRideCannotBeCompleted() {
        stored(RideStatus.REQUESTED);
        hasStatus(() -> service.complete("ride-1", new CompleteRideRequest(BigDecimal.ONE), driver), 403);
        verifyNoInteractions(fares);
    }

    @Test void completionStoresFareDistanceAndReleasesDriver() {
        stored(RideStatus.IN_PROGRESS);
        mappedDriver();
        when(fares.calculate(eq("ride-1"), eq(BigDecimal.TEN), anyLong())).thenReturn(fare);
        Ride completed = service.complete("ride-1", new CompleteRideRequest(BigDecimal.TEN), driver);
        assertThat(completed.status()).isEqualTo(RideStatus.COMPLETED);
        assertThat(completed.fare()).isEqualTo(fare);
        assertThat(completed.distanceKm()).isEqualTo(BigDecimal.TEN);
        assertThat(completed.completedAt()).isNotNull();
        assertThat(completed.activeDriverId()).isNull();
    }

    @Test void nonpositiveCompletionDistanceIsRejected() {
        stored(RideStatus.IN_PROGRESS);
        mappedDriver();
        hasStatus(() -> service.complete("ride-1", new CompleteRideRequest(BigDecimal.ZERO), driver), 400);
        verifyNoInteractions(fares);
    }

    @Test void fareFailureDoesNotCompleteRide() {
        stored(RideStatus.IN_PROGRESS);
        mappedDriver();
        when(fares.calculate(anyString(), any(), anyLong()))
                .thenThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Fare unavailable"));
        hasStatus(() -> service.complete("ride-1", new CompleteRideRequest(BigDecimal.ONE), driver), 503);
        verify(repository, never()).save(any());
    }

    @Test void driverFailureDoesNotAssignRide() {
        stored(RideStatus.REQUESTED);
        when(drivers.byId(anyString()))
                .thenThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Driver unavailable"));
        hasStatus(() -> service.assign("ride-1", "profile-1", admin), 503);
        verify(repository, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = RideStatus.class, names = {"COMPLETED", "CANCELLED"}, mode = EnumSource.Mode.EXCLUDE)
    void nonterminalRidesCanBeCancelled(RideStatus status) {
        stored(status);
        Ride cancelled = service.cancel("ride-1", new CancelRideRequest("Plans changed"), rider);
        assertThat(cancelled.status()).isEqualTo(RideStatus.CANCELLED);
        assertThat(cancelled.activeDriverId()).isNull();
        assertThat(cancelled.cancelledAt()).isNotNull();
        assertThat(cancelled.cancellationReason()).isEqualTo("Plans changed");
    }

    @ParameterizedTest
    @EnumSource(value = RideStatus.class, names = {"COMPLETED", "CANCELLED"})
    void terminalRidesCannotBeCancelled(RideStatus status) {
        stored(status);
        hasStatus(() -> service.cancel("ride-1", new CancelRideRequest(null), rider), 409);
    }

    @Test void anotherRiderCannotCancel() {
        stored(RideStatus.REQUESTED);
        hasStatus(() -> service.cancel("ride-1", new CancelRideRequest(null),
                new UserPrincipal("other", "RIDER")), 403);
    }

    @Test void adminCanCancel() {
        stored(RideStatus.REQUESTED);
        assertThat(service.cancel("ride-1", new CancelRideRequest(null), admin).status())
                .isEqualTo(RideStatus.CANCELLED);
    }

    @Test void paymentContextUsesStoredIdentityAndFare() {
        stored(RideStatus.COMPLETED);
        PaymentContext context = service.paymentContext("ride-1");
        assertThat(context.riderId()).isEqualTo(rider.userId());
        assertThat(context.driverId()).isEqualTo("profile-1");
        assertThat(context.fare()).isEqualTo(fare);
    }

    @ParameterizedTest
    @EnumSource(value = RideStatus.class, names = "COMPLETED", mode = EnumSource.Mode.EXCLUDE)
    void paymentContextRequiresCompletedRide(RideStatus status) {
        stored(status);
        hasStatus(() -> service.paymentContext("ride-1"), 409);
    }
}
