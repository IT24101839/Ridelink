package com.ridelink.farepayment.controller;
import com.ridelink.farepayment.dto.*;
import com.ridelink.farepayment.model.*;
import com.ridelink.farepayment.security.UserPrincipal;
import com.ridelink.farepayment.service.FarePaymentService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/fare-payment")
public class FarePaymentController {
    private final FarePaymentService service;
    public FarePaymentController(FarePaymentService service){this.service=service;}
    @SecurityRequirement(name = "serviceToken")
    @PostMapping("/fares/calculate") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('SERVICE')")
    public Fare calculate(@Valid @RequestBody FareRequest request,@RequestHeader("Idempotency-Key") String key){return service.calculateFare(request,key);}
    @SecurityRequirement(name = "serviceToken")
    @GetMapping("/fares/ride/{rideId}") @PreAuthorize("hasRole('SERVICE')")
    public Fare fare(@PathVariable String rideId){return service.getFareByRideId(rideId);}
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/payments") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('RIDER')")
    public Payment create(@Valid @RequestBody PaymentRequest r,@AuthenticationPrincipal UserPrincipal user){return service.createPayment(r,user);}
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/payments/{id}") @PreAuthorize("hasAnyRole('RIDER','ADMIN')")
    public Payment get(@PathVariable String id,@AuthenticationPrincipal UserPrincipal user){return service.ownedPayment(id,user);}
    // Simulated outcome only. No card details, gateway request, or actual money movement.
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/payments/{id}/process") @PreAuthorize("hasAnyRole('RIDER','ADMIN')")
    public Payment process(@PathVariable String id,@Valid @RequestBody ProcessRequest r,@AuthenticationPrincipal UserPrincipal user){return service.process(id,r.success(),user);}
    @SecurityRequirement(name = "serviceToken")
    @PutMapping("/payments/{id}/status") @PreAuthorize("hasRole('SERVICE')")
    public Payment status(@PathVariable String id,@Valid @RequestBody PaymentStatusRequest r){return service.updatePaymentStatus(id,r.status());}
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/payments/{id}/receipt") @PreAuthorize("hasAnyRole('RIDER','ADMIN')")
    public Receipt receipt(@PathVariable String id,@AuthenticationPrincipal UserPrincipal user){return service.receipt(id,user);}
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/payments/{id}/receipt") @PreAuthorize("hasAnyRole('RIDER','ADMIN')")
    public Receipt getReceipt(@PathVariable String id,@AuthenticationPrincipal UserPrincipal user){return service.getReceipt(id,user);}
}
