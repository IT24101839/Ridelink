package com.ridelink.ride.model;

import java.math.BigDecimal;

public record FareDetails(String fareId, BigDecimal totalAmount, String currency) {
}
