package com.ridelink.ride.client;
import com.ridelink.ride.exception.ApiException;
import org.junit.jupiter.api.*;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
class DriverReservationClientTest {
    MockRestServiceServer server;DriverServiceClient client;
    @BeforeEach void setup(){var b=RestClient.builder();server=MockRestServiceServer.bindTo(b).build();client=new DriverServiceClient(b,"http://driver.test","token");}
    @Test void reserveUsesExactContract(){
        server.expect(requestTo("http://driver.test/api/internal/drivers/d1/reservation")).andExpect(method(HttpMethod.PUT))
                .andExpect(header("X-Service-Token","token")).andExpect(content().json("{\"rideId\":\"r1\"}")).andRespond(withSuccess());
        client.reserve("d1","r1");server.verify();
    }
    @Test void releaseUsesExactContract(){
        server.expect(requestTo("http://driver.test/api/internal/drivers/d1/reservation/r1")).andExpect(method(HttpMethod.DELETE))
                .andExpect(header("X-Service-Token","token")).andRespond(withSuccess());
        client.release("d1","r1");server.verify();
    }
    @Test void reservationConflictIs409(){
        server.expect(requestTo("http://driver.test/api/internal/drivers/d1/reservation")).andRespond(withStatus(HttpStatus.CONFLICT));
        assertThatThrownBy(()->client.reserve("d1","r1")).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT));
    }
    @Test void releaseOutageIs503(){
        server.expect(requestTo("http://driver.test/api/internal/drivers/d1/reservation/r1")).andRespond(withServerError());
        assertThatThrownBy(()->client.release("d1","r1")).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }
}
