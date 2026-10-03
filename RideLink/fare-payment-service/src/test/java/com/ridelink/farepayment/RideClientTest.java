package com.ridelink.farepayment;
import com.ridelink.farepayment.client.RideServiceClient;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import java.net.SocketTimeoutException;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
class RideClientTest {
    MockRestServiceServer server;RideServiceClient client;
    @BeforeEach void setup(){
        var builder=RestClient.builder().baseUrl("http://ride.test");
        server=MockRestServiceServer.bindTo(builder).build();
        client=new RideServiceClient(builder.build(),"ride-token");
    }
    @Test void contextPathHeaderAndStringIdentities(){
        server.expect(requestTo("http://ride.test/api/internal/rides/r1/payment-context"))
                .andExpect(header("X-Service-Token","ride-token"))
                .andRespond(withSuccess("""
                {"rideId":"r1","riderId":"account-1","driverId":"profile-1","status":"COMPLETED",
                 "fare":{"fareId":"f1","totalAmount":100.00,"currency":"LKR"}}
                """,MediaType.APPLICATION_JSON));
        assertThat(client.context("r1").riderId()).isEqualTo("account-1");server.verify();
    }
    @ParameterizedTest @ValueSource(ints={401,403,500,503})
    void upstreamFailuresAre503(int code){
        server.expect(requestTo("http://ride.test/api/internal/rides/r1/payment-context")).andRespond(withStatus(HttpStatusCode.valueOf(code)));
        failure(503);
    }
    @ParameterizedTest @ValueSource(ints={404,409})
    void missingAndNonCompletedRemainDomainErrors(int code){
        server.expect(requestTo("http://ride.test/api/internal/rides/r1/payment-context")).andRespond(withStatus(HttpStatusCode.valueOf(code)));
        failure(code);
    }
    @Test void timeoutIs503(){
        server.expect(requestTo("http://ride.test/api/internal/rides/r1/payment-context")).andRespond(withException(new SocketTimeoutException()));
        failure(503);
    }
    @Test void rejectsSuccessfulResponseForNonCompletedRide(){
        server.expect(requestTo("http://ride.test/api/internal/rides/r1/payment-context")).andRespond(withSuccess(
                "{\"rideId\":\"r1\",\"riderId\":\"a\",\"driverId\":\"d\",\"status\":\"IN_PROGRESS\"}",MediaType.APPLICATION_JSON));
        failure(409);
    }
    @Test void identityMismatchIs503(){
        server.expect(requestTo("http://ride.test/api/internal/rides/r1/payment-context")).andRespond(withSuccess(
                "{\"rideId\":\"other\"}",MediaType.APPLICATION_JSON));failure(503);
    }
    void failure(int code){
        assertThatThrownBy(()->client.context("r1")).isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(code));
        server.verify();
    }
}
