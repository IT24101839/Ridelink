package com.ridelink.farepayment.controller;

import com.ridelink.farepayment.dto.FareRequest;
import com.ridelink.farepayment.dto.PaymentRequest;
import com.ridelink.farepayment.dto.PaymentStatusRequest;
import com.ridelink.farepayment.model.Fare;
import com.ridelink.farepayment.model.Payment;
import com.ridelink.farepayment.service.FarePaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/fare-payment")
public class FarePaymentController {
    private final FarePaymentService farePaymentService;

    public FarePaymentController(FarePaymentService farePaymentService) {
        this.farePaymentService = farePaymentService;
    }

    @PostMapping("/fares/calculate")
    @ResponseStatus(HttpStatus.CREATED)
    public Fare calculateFare(@Valid @RequestBody FareRequest request) {
        return farePaymentService.calculateFare(request);
    }

    @GetMapping("/fares/ride/{rideId}")
    public Fare getFare(@PathVariable Long rideId) {
        return farePaymentService.getFareByRideId(rideId);
    }

    @PostMapping("/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public Payment createPayment(@Valid @RequestBody PaymentRequest request) {
        return farePaymentService.createPayment(request);
    }

    @GetMapping("/payments/{paymentId}")
    public Payment getPayment(@PathVariable String paymentId) {
        return farePaymentService.getPayment(paymentId);
    }

    @PutMapping("/payments/{paymentId}/status")
    public Payment updatePaymentStatus(@PathVariable String paymentId,
                                       @Valid @RequestBody PaymentStatusRequest request) {
        return farePaymentService.updatePaymentStatus(paymentId, request.status());
    }
}
