package com.ridelink.farepayment.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "fares")
public class Fare {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long rideId;
    private BigDecimal distanceKm;
    private Integer durationMinutes;
    private BigDecimal totalAmount;
    private String currency;
    private LocalDateTime createdAt;

    protected Fare() {
    }

    public Fare(Long rideId, BigDecimal distanceKm, Integer durationMinutes, BigDecimal totalAmount, String currency) {
        this.rideId = rideId;
        this.distanceKm = distanceKm;
        this.durationMinutes = durationMinutes;
        this.totalAmount = totalAmount;
        this.currency = currency;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getRideId() {
        return rideId;
    }

    public BigDecimal getDistanceKm() {
        return distanceKm;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
