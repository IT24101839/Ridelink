package com.ridelink.farepayment.model;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;
import java.math.BigDecimal;
import java.time.Instant;
@Document("fares")
public record Fare(@Id String id,@Indexed(unique=true) String rideId,BigDecimal distanceKm,
        long durationMinutes,BigDecimal totalAmount,String currency,Instant createdAt,String idempotencyKey) {}
