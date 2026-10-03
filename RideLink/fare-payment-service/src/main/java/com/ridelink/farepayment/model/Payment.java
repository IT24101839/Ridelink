package com.ridelink.farepayment.model;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.data.annotation.*;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;
import java.math.BigDecimal;
import java.time.Instant;
@Document("payments")
public record Payment(@Id String id,@Indexed(unique=true) String rideId,String riderId,String fareId,
        BigDecimal amount,String currency,String paymentMethod,PaymentStatus status,
        String transactionReference,Instant createdAt,Instant paidAt,@Version @JsonIgnore Long version) {
    public Payment processed(PaymentStatus next){
        boolean paid=next==PaymentStatus.COMPLETED;
        return new Payment(id,rideId,riderId,fareId,amount,currency,paymentMethod,next,
                paid ? "SIM-"+java.util.UUID.randomUUID() : null,createdAt,paid ? Instant.now() : null,version);
    }
}
