package com.ridelink.farepayment;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridelink.farepayment.client.RideServiceClient;
import com.ridelink.farepayment.config.SecurityConfig;
import com.ridelink.farepayment.controller.FarePaymentController;
import com.ridelink.farepayment.exception.GlobalExceptionHandler;
import com.ridelink.farepayment.model.*;
import com.ridelink.farepayment.repository.*;
import com.ridelink.farepayment.service.FarePaymentService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers=FarePaymentController.class,properties={
    "security.jwt-secret=01234567890123456789012345678901","security.internal-token=fare-test-token",
    "spring.jackson.deserialization.fail-on-unknown-properties=true"})
@Import({SecurityConfig.class,FarePaymentService.class,GlobalExceptionHandler.class})
class PaymentIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockBean FareRepository fares;
    @MockBean PaymentRepository payments;
    @MockBean ReceiptRepository receipts;
    @MockBean RideServiceClient rides;
    final Map<String,Fare> fareStore=new ConcurrentHashMap<>();
    final Map<String,Payment> paymentStore=new ConcurrentHashMap<>();
    final Map<String,Receipt> receiptStore=new ConcurrentHashMap<>();
    @BeforeEach void setup(){
        when(fares.findById(anyString())).thenAnswer(i->Optional.ofNullable(fareStore.get(i.getArgument(0))));
        when(fares.insert(any(Fare.class))).thenAnswer(i->{Fare f=i.getArgument(0);if(fareStore.putIfAbsent(f.id(),f)!=null)throw new DuplicateKeyException("duplicate");return f;});
        when(payments.findById(anyString())).thenAnswer(i->Optional.ofNullable(paymentStore.get(i.getArgument(0))));
        when(payments.insert(any(Payment.class))).thenAnswer(i->{Payment p=i.getArgument(0);if(paymentStore.putIfAbsent(p.id(),p)!=null)throw new DuplicateKeyException("duplicate");return p;});
        when(payments.save(any(Payment.class))).thenAnswer(i->{Payment p=i.getArgument(0);paymentStore.put(p.id(),p);return p;});
        when(receipts.findById(anyString())).thenAnswer(i->Optional.ofNullable(receiptStore.get(i.getArgument(0))));
        when(receipts.insert(any(Receipt.class))).thenAnswer(i->{Receipt r=i.getArgument(0);if(receiptStore.putIfAbsent(r.paymentId(),r)!=null)throw new DuplicateKeyException("duplicate");return r;});
        when(rides.context("ride-1")).thenReturn(new RideServiceClient.Context("ride-1","rider-1","driver-1","COMPLETED",
                new RideServiceClient.FareContext("fare-1",new BigDecimal("636.00"),"LKR")));
    }
    String token(String user,String role){
        return "Bearer "+Jwts.builder().subject("rider@example.com").claim("userId",user).claim("role",role)
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor("01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8))).compact();
    }
    void create() throws Exception {
        mvc.perform(post("/api/fare-payment/payments").header("Authorization",token("rider-1","PASSENGER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"rideId\":\"ride-1\",\"paymentMethod\":\"CARD\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.riderId").value("rider-1"))
                .andExpect(jsonPath("$.amount").value(636)).andExpect(jsonPath("$.currency").value("LKR"));
    }
    @Test void fareAcceptsStringIdsAndRetriesIdempotently() throws Exception {
        String body="{\"rideId\":\"mongo-ride\",\"distanceKm\":5.2,\"durationMinutes\":12}";
        for(int n=0;n<2;n++)mvc.perform(post("/api/fare-payment/fares/calculate").header("X-Service-Token","fare-test-token")
                .header("Idempotency-Key","ride-completion-mongo-ride").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value("fare-mongo-ride"))
                .andExpect(jsonPath("$.rideId").value("mongo-ride")).andExpect(jsonPath("$.totalAmount").value(636));
        assertThat(fareStore).hasSize(1);
    }
    @Test void conflictingFareDataIs409() throws Exception {
        for(int n=0;n<2;n++)mvc.perform(post("/api/fare-payment/fares/calculate").header("X-Service-Token","fare-test-token")
                .header("Idempotency-Key","ride-completion-r1").contentType(MediaType.APPLICATION_JSON)
                .content("{\"rideId\":\"r1\",\"distanceKm\":"+(n+1)+",\"durationMinutes\":1}"))
                .andExpect(status().is(n==0?201:409));
    }
    @Test void incorrectIdempotencyKeyIs400() throws Exception {
        mvc.perform(post("/api/fare-payment/fares/calculate").header("X-Service-Token","fare-test-token")
                .header("Idempotency-Key","wrong").contentType(MediaType.APPLICATION_JSON)
                .content("{\"rideId\":\"r1\",\"distanceKm\":1,\"durationMinutes\":1}")).andExpect(status().isBadRequest());
    }
    @Test void finalFareRequiresServiceTokenNotUserJwt() throws Exception {
        mvc.perform(post("/api/fare-payment/fares/calculate").header("Authorization",token("admin","ADMIN"))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/fare-payment/fares/calculate").header("X-Service-Token","wrong")).andExpect(status().isUnauthorized());
    }
    @Test void paymentUsesTrustedContext() throws Exception {create();verify(rides).context("ride-1");}
    @ParameterizedTest @ValueSource(strings={"\"riderId\":\"victim\"","\"userId\":\"victim\"","\"amount\":1"})
    void extraIdentityOrAmountIsRejected(String extra) throws Exception {
        mvc.perform(post("/api/fare-payment/payments").header("Authorization",token("rider-1","PASSENGER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"rideId\":\"ride-1\",\"paymentMethod\":\"CARD\","+extra+"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(rides);
    }
    @Test void wrongRiderIs403() throws Exception {
        mvc.perform(post("/api/fare-payment/payments").header("Authorization",token("other","PASSENGER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"rideId\":\"ride-1\",\"paymentMethod\":\"CARD\"}")).andExpect(status().isForbidden());
    }
    @Test void wrongRoleIs403() throws Exception {
        mvc.perform(post("/api/fare-payment/payments").header("Authorization",token("driver","DRIVER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"rideId\":\"ride-1\",\"paymentMethod\":\"CARD\"}")).andExpect(status().isForbidden());
    }
    @Test void missingAndInvalidJwtAre401() throws Exception {
        mvc.perform(get("/api/fare-payment/payments/p1")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/fare-payment/payments/p1").header("Authorization","Bearer bad")).andExpect(status().isUnauthorized());
    }
    @Test void nonCompletedRideIs409() throws Exception {
        when(rides.context("ride-1")).thenThrow(new ResponseStatusException(HttpStatus.CONFLICT,"Ride is not completed"));
        mvc.perform(post("/api/fare-payment/payments").header("Authorization",token("rider-1","PASSENGER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"rideId\":\"ride-1\",\"paymentMethod\":\"CARD\"}")).andExpect(status().isConflict());
    }
    @Test void rideOutageIs503() throws Exception {
        when(rides.context("ride-1")).thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Ride unavailable"));
        mvc.perform(post("/api/fare-payment/payments").header("Authorization",token("rider-1","PASSENGER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"rideId\":\"ride-1\",\"paymentMethod\":\"CARD\"}")).andExpect(status().isServiceUnavailable());
    }
    @Test void duplicatePaymentIs409() throws Exception {
        create();
        mvc.perform(post("/api/fare-payment/payments").header("Authorization",token("rider-1","PASSENGER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"rideId\":\"ride-1\",\"paymentMethod\":\"CARD\"}")).andExpect(status().isConflict());
    }
    @Test void successfulProcessingAndReceiptAreIdempotent() throws Exception {
        create();
        for(int n=0;n<2;n++)mvc.perform(post("/api/fare-payment/payments/payment-ride-1/process").header("Authorization",token("rider-1","PASSENGER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"success\":true}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED")).andExpect(jsonPath("$.transactionReference").exists()).andExpect(jsonPath("$.paidAt").exists());
        String first=null;
        for(int n=0;n<2;n++){
            String result=mvc.perform(post("/api/fare-payment/payments/payment-ride-1/receipt").header("Authorization",token("rider-1","PASSENGER")))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.amount").value(636)).andExpect(jsonPath("$.riderId").value("rider-1"))
                    .andReturn().getResponse().getContentAsString();
            if(first==null)first=result;else assertThat(result).isEqualTo(first);
        }
        mvc.perform(get("/api/fare-payment/payments/payment-ride-1/receipt").header("Authorization",token("other","PASSENGER"))).andExpect(status().isForbidden());
        assertThat(receiptStore).hasSize(1);
    }
    @Test void failedPaymentCannotBecomeSuccessfulOrIssueReceipt() throws Exception {
        create();
        mvc.perform(post("/api/fare-payment/payments/payment-ride-1/receipt").header("Authorization",token("rider-1","PASSENGER"))).andExpect(status().isConflict());
        mvc.perform(post("/api/fare-payment/payments/payment-ride-1/process").header("Authorization",token("rider-1","PASSENGER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"success\":false}")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FAILED"));
        mvc.perform(post("/api/fare-payment/payments/payment-ride-1/process").header("Authorization",token("rider-1","PASSENGER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"success\":true}")).andExpect(status().isConflict());
        mvc.perform(post("/api/fare-payment/payments/payment-ride-1/receipt").header("Authorization",token("rider-1","PASSENGER"))).andExpect(status().isConflict());
    }
    @Test void wrongOwnerCannotProcessAndLegacyStatusRequiresServiceToken() throws Exception {
        create();
        mvc.perform(post("/api/fare-payment/payments/payment-ride-1/process").header("Authorization",token("other","PASSENGER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"success\":true}")).andExpect(status().isForbidden());
        mvc.perform(put("/api/fare-payment/payments/payment-ride-1/status").header("Authorization",token("rider-1","PASSENGER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"COMPLETED\"}")).andExpect(status().isUnauthorized());
    }
    @Test void missingPaymentIs404() throws Exception {
        mvc.perform(get("/api/fare-payment/payments/missing").header("Authorization",token("rider-1","PASSENGER"))).andExpect(status().isNotFound());
    }
}
