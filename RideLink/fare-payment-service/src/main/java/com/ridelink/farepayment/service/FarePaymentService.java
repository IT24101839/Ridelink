package com.ridelink.farepayment.service;

import com.ridelink.farepayment.dto.FareRequest;
import com.ridelink.farepayment.dto.PaymentRequest;
import com.ridelink.farepayment.model.PaymentStatus;
import com.ridelink.farepayment.model.Fare;
import com.ridelink.farepayment.model.Payment;
import com.ridelink.farepayment.repository.FareRepository;
import com.ridelink.farepayment.repository.PaymentRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Service
public class FarePaymentService {
    private static final BigDecimal BASE_FARE = new BigDecimal("100.00");
    private static final BigDecimal RATE_PER_KILOMETER = new BigDecimal("80.00");
    private static final BigDecimal RATE_PER_MINUTE = new BigDecimal("10.00");

    private final FareRepository fareRepository;
    private final PaymentRepository paymentRepository;

    public FarePaymentService(FareRepository fareRepository, PaymentRepository paymentRepository) {
        this.fareRepository = fareRepository;
        this.paymentRepository = paymentRepository;
    }

    public Fare calculateFare(FareRequest request) {
        BigDecimal distanceCharge = request.distanceKm().multiply(RATE_PER_KILOMETER);
        BigDecimal durationCharge = BigDecimal.valueOf(request.durationMinutes()).multiply(RATE_PER_MINUTE);
        BigDecimal total = BASE_FARE.add(distanceCharge).add(durationCharge).setScale(2, RoundingMode.HALF_UP);

        return fareRepository.save(new Fare(
                request.rideId(),
                BASE_FARE,
                distanceCharge,
                durationCharge,
                total,
                LocalDateTime.now()
        ));
    }

    public Payment createPayment(PaymentRequest request) {
        // Find by String id for MongoDB
        Fare fare = fareRepository.findById(request.fareId())
                .orElseThrow(() -> new IllegalArgumentException("Fare not found: " + request.fareId()));

        if (!fare.getRideId().equals(request.rideId())) {
            throw new IllegalArgumentException("Fare does not belong to the supplied ride");
        }

        return paymentRepository.save(new Payment(
                request.rideId(),
                request.userId(),
                fare.getTotalFare(),
                request.paymentMethod(),
                PaymentStatus.PENDING,
                LocalDateTime.now()
        ));
    }

    public Fare getFareByRideId(Long rideId) {
        return fareRepository.findByRideId(rideId)
                .orElseThrow(() -> new IllegalArgumentException("Fare not found for ride: " + rideId));
    }

    public Payment getPayment(String paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));
    }

    public Payment updatePaymentStatus(String paymentId, PaymentStatus newStatus) {
        Payment payment = getPayment(paymentId);
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new IllegalStateException("Only pending payments can be updated");
        }
        payment.setStatus(newStatus);
        return paymentRepository.save(payment);
    }
}
