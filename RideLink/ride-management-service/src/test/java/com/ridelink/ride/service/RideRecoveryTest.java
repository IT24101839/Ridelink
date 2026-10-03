package com.ridelink.ride.service;
import com.ridelink.ride.client.*;
import com.ridelink.ride.client.DriverServiceClient.DriverProfile;
import com.ridelink.ride.dto.*;
import com.ridelink.ride.exception.ApiException;
import com.ridelink.ride.model.*;
import com.ridelink.ride.repository.RideRepository;
import com.ridelink.ride.security.UserPrincipal;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;
class RideRecoveryTest {
    RideRepository repository;DriverServiceClient drivers;FareServiceClient fares;RideService service;
    AtomicReference<Ride> current=new AtomicReference<>();
    UserPrincipal admin=new UserPrincipal("admin","ADMIN"),driver=new UserPrincipal("account-driver","DRIVER"),rider=new UserPrincipal("rider","RIDER");
    @BeforeEach void setup(){
        repository=mock(RideRepository.class);drivers=mock(DriverServiceClient.class);fares=mock(FareServiceClient.class);
        service=new RideService(repository,drivers,fares);
        when(repository.findById("r1")).thenAnswer(i->Optional.of(current.get()));
        when(repository.save(any(Ride.class))).thenAnswer(i->{Ride r=i.getArgument(0);current.set(r);return r;});
        when(drivers.byId("d1")).thenReturn(new DriverProfile("d1","account-driver","AVAILABLE"));
        when(drivers.byAccountId("account-driver")).thenReturn(new DriverProfile("d1","account-driver","BUSY"));
        when(fares.calculate(anyString(),any(),anyLong())).thenReturn(new FareDetails("f1",BigDecimal.TEN,"LKR"));
        when(repository.findByStatusAndActiveDriverIdIsNotNull(any(),any())).thenReturn(List.of());
        when(repository.findByStatusAndCompletionRequestedAtIsNotNull(any(),any())).thenReturn(List.of());
        when(repository.findByReleaseDriverIdIsNotNull(any())).thenReturn(List.of());
    }
    Ride fixture(RideStatus status){
        Instant now=Instant.now().minusSeconds(600);Location loc=new Location("Place",1.0,2.0);
        return new Ride("r1","rider",status==RideStatus.REQUESTED?null:"d1",status,loc,loc,null,null,
                now,now,now,now,null,null,null,status==RideStatus.REQUESTED?null:"d1",1L);
    }
    @Test void assignmentIntentSurvivesTimeoutAndRetryReservesSameRide(){
        current.set(fixture(RideStatus.REQUESTED));
        doThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"timeout")).doNothing().when(drivers).reserve("d1","r1");
        assertThatThrownBy(()->service.assign("r1","d1",admin)).isInstanceOf(ApiException.class);
        assertThat(current.get().status()).isEqualTo(RideStatus.REQUESTED);
        assertThat(current.get().activeDriverId()).isEqualTo("d1");
        assertThat(service.assign("r1","d1",admin).status()).isEqualTo(RideStatus.ASSIGNED);
        verify(drivers,times(2)).reserve("d1","r1");verify(drivers,times(1)).byId("d1");
    }
    @Test void reservationConflictClearsUnfulfilledIntent(){
        current.set(fixture(RideStatus.REQUESTED));
        doThrow(new ApiException(HttpStatus.CONFLICT,"busy")).when(drivers).reserve("d1","r1");
        assertThatThrownBy(()->service.assign("r1","d1",admin)).isInstanceOf(ApiException.class);
        assertThat(current.get().activeDriverId()).isNull();assertThat(current.get().status()).isEqualTo(RideStatus.REQUESTED);
    }
    @Test void completionRetryKeepsDistanceAndDuration(){
        current.set(fixture(RideStatus.IN_PROGRESS));
        when(fares.calculate(anyString(),any(),anyLong())).thenThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"timeout"))
                .thenReturn(new FareDetails("f1",BigDecimal.TEN,"LKR"));
        assertThatThrownBy(()->service.complete("r1",new CompleteRideRequest(BigDecimal.ONE),driver)).isInstanceOf(ApiException.class);
        Instant frozen=current.get().completionRequestedAt();
        assertThat(frozen).isNotNull();
        assertThatThrownBy(()->service.complete("r1",new CompleteRideRequest(BigDecimal.TEN),driver)).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->service.cancel("r1",new CancelRideRequest("cancel"),rider)).isInstanceOf(ApiException.class);
        Ride result=service.complete("r1",new CompleteRideRequest(BigDecimal.ONE),driver);
        assertThat(result.completedAt()).isEqualTo(frozen);assertThat(result.status()).isEqualTo(RideStatus.COMPLETED);
        var duration=org.mockito.ArgumentCaptor.forClass(Long.class);
        verify(fares,times(2)).calculate(eq("r1"),eq(BigDecimal.ONE),duration.capture());
        assertThat(duration.getAllValues().get(0)).isEqualTo(duration.getAllValues().get(1));
    }
    @Test void releaseFailureIsPersistedAndRecovered(){
        current.set(fixture(RideStatus.ACCEPTED));
        doThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"down")).doNothing().when(drivers).release("d1","r1");
        Ride result=service.cancel("r1",new CancelRideRequest("cancel"),rider);
        assertThat(result.status()).isEqualTo(RideStatus.CANCELLED);assertThat(result.driverReleasePending()).isTrue();
        when(repository.findByReleaseDriverIdIsNotNull(any())).thenReturn(List.of(current.get()));
        service.recoverPendingOperations();
        assertThat(current.get().driverReleasePending()).isFalse();verify(drivers,times(2)).release("d1","r1");
    }
    @Test void recoveryFinishesPersistedAssignmentAndCompletion(){
        current.set(fixture(RideStatus.REQUESTED).assignmentIntent("d1"));
        when(repository.findByStatusAndActiveDriverIdIsNotNull(any(),any())).thenReturn(List.of(current.get()));
        service.recoverPendingOperations();assertThat(current.get().status()).isEqualTo(RideStatus.ASSIGNED);
        when(repository.findByStatusAndActiveDriverIdIsNotNull(any(),any())).thenReturn(List.of());
        current.set(fixture(RideStatus.IN_PROGRESS).completionIntent(BigDecimal.ONE,Instant.now()));
        when(repository.findByStatusAndCompletionRequestedAtIsNotNull(any(),any())).thenReturn(List.of(current.get()));
        service.recoverPendingOperations();assertThat(current.get().status()).isEqualTo(RideStatus.COMPLETED);
    }
    @Test void oldReleaseCannotReleaseNewReservation(){
        current.set(fixture(RideStatus.ACCEPTED));
        doThrow(new ApiException(HttpStatus.CONFLICT,"new reservation")).when(drivers).release("d1","r1");
        assertThat(service.cancel("r1",new CancelRideRequest(null),rider).driverReleasePending()).isFalse();
    }
}
