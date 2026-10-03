package com.ridelink.farepayment.model;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.math.BigDecimal;
import java.time.Instant;
@Document("receipts")
public record Receipt(@Id String paymentId,String rideId,String riderId,BigDecimal amount,String currency,
        String transactionReference,Instant paidAt,Instant issuedAt) {}
