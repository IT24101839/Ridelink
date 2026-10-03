package com.ridelink.farepayment.client;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.*;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
@Component
public class RideServiceClient {
    private final RestClient client;
    private final String token;
    @org.springframework.beans.factory.annotation.Autowired
    public RideServiceClient(RestClient.Builder builder,@Value("${payment.ride-url}") String url,
            @Value("${payment.ride-token:}") String token,
            @Value("${payment.connect-timeout-ms:2000}") int connect,
            @Value("${payment.read-timeout-ms:5000}") int read){
        if(connect<=0||read<=0)throw new IllegalArgumentException("Timeouts must be positive");
        var factory=new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connect);factory.setReadTimeout(read);
        client=builder.clone().baseUrl(url).requestFactory(factory).build();this.token=token;
    }
    // Test constructor retains the same request implementation with a mock HTTP transport.
    public RideServiceClient(RestClient client,String token){this.client=client;this.token=token;}
    public record FareContext(String fareId,BigDecimal totalAmount,String currency){}
    public record Context(String rideId,String riderId,String driverId,String status,FareContext fare){}
    public Context context(String rideId){
        if(token.isBlank())throw unavailable();
        try{
            Context c=client.get().uri("/api/internal/rides/{id}/payment-context",rideId)
                    .header("X-Service-Token",token).retrieve().body(Context.class);
            if(c==null||!rideId.equals(c.rideId())||c.riderId()==null||c.riderId().isBlank()
                    ||c.driverId()==null||c.driverId().isBlank()||c.status()==null)throw unavailable();
            if(!"COMPLETED".equals(c.status()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Ride is not completed");
            if(c.fare()==null||c.fare().fareId()==null||c.fare().fareId().isBlank()
                    ||c.fare().totalAmount()==null||c.fare().totalAmount().signum()<0
                    ||c.fare().currency()==null||!c.fare().currency().matches("[A-Z]{3}"))throw unavailable();
            return c;
        }catch(RestClientResponseException ex){
            if(ex.getStatusCode().value()==404)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Ride not found");
            if(ex.getStatusCode().value()==409)throw new ResponseStatusException(HttpStatus.CONFLICT,"Ride is not completed");
            throw unavailable();
        }catch(RestClientException ex){throw unavailable();}
    }
    private ResponseStatusException unavailable(){return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Ride Service unavailable");}
}
