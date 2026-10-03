package com.ridelink.farepayment.service;

import com.ridelink.farepayment.client.RideServiceClient;
import com.ridelink.farepayment.dto.*;
import com.ridelink.farepayment.model.*;
import com.ridelink.farepayment.repository.*;
import com.ridelink.farepayment.security.UserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.math.*;
import java.time.Instant;

@Service
public class FarePaymentService {
    private final FareRepository fares;
    private final PaymentRepository payments;
    private final ReceiptRepository receipts;
    private final RideServiceClient rides;
    public FarePaymentService(FareRepository fares,PaymentRepository payments,ReceiptRepository receipts,RideServiceClient rides){
        this.fares=fares;this.payments=payments;this.receipts=receipts;this.rides=rides;
    }
    public Fare calculateFare(FareRequest r,String key){
        if(!("ride-completion-"+r.rideId()).equals(key))throw error(HttpStatus.BAD_REQUEST,"Invalid Idempotency-Key");
        String id="fare-"+r.rideId();
        var existing=fares.findById(id);
        if(existing.isPresent())return sameFare(existing.get(),r,key);
        BigDecimal amount=new BigDecimal("100.00").add(r.distanceKm().multiply(new BigDecimal("80.00")))
                .add(BigDecimal.valueOf(r.durationMinutes()).multiply(new BigDecimal("10.00"))).setScale(2,RoundingMode.HALF_UP);
        Fare fare=new Fare(id,r.rideId(),r.distanceKm(),r.durationMinutes(),amount,"LKR",Instant.now(),key);
        try{return fares.insert(fare);}
        catch(DuplicateKeyException ex){
            return sameFare(fares.findById(id).orElseThrow(()->error(HttpStatus.CONFLICT,"Fare already exists")),r,key);
        }
    }
    private Fare sameFare(Fare fare,FareRequest r,String key){
        if(!fare.rideId().equals(r.rideId())||!fare.idempotencyKey().equals(key)
                ||fare.distanceKm().compareTo(r.distanceKm())!=0||fare.durationMinutes()!=r.durationMinutes())
            throw error(HttpStatus.CONFLICT,"Idempotency key reused with different trip data");
        return fare;
    }
    public Fare getFareByRideId(String rideId){return fares.findByRideId(rideId).orElseThrow(()->error(HttpStatus.NOT_FOUND,"Fare not found"));}
    public Payment createPayment(PaymentRequest r,UserPrincipal user){
        var c=rides.context(r.rideId());
        if(!user.userId().equals(c.riderId()))throw error(HttpStatus.FORBIDDEN,"Ride belongs to another rider");
        Payment p=new Payment("payment-"+r.rideId(),r.rideId(),c.riderId(),c.fare().fareId(),
                c.fare().totalAmount(),c.fare().currency(),r.paymentMethod(),PaymentStatus.PENDING,null,Instant.now(),null,null);
        try{return payments.insert(p);}
        catch(DuplicateKeyException ex){throw error(HttpStatus.CONFLICT,"Payment already exists for ride");}
    }
    public Payment getPayment(String id){return payments.findById(id).orElseThrow(()->error(HttpStatus.NOT_FOUND,"Payment not found"));}
    public Payment ownedPayment(String id,UserPrincipal user){
        Payment p=getPayment(id);
        if(!user.isAdmin()&&!p.riderId().equals(user.userId()))throw error(HttpStatus.FORBIDDEN,"Payment belongs to another rider");
        return p;
    }
    public Payment process(String id,boolean success,UserPrincipal user){
        Payment p=ownedPayment(id,user);
        return transition(p,success?PaymentStatus.COMPLETED:PaymentStatus.FAILED);
    }
    public Payment updatePaymentStatus(String id,PaymentStatus status){return transition(getPayment(id),status);}
    private Payment transition(Payment p,PaymentStatus status){
        if(status==PaymentStatus.PENDING)throw error(HttpStatus.CONFLICT,"Cannot process to PENDING");
        if(p.status()==status)return p;
        if(p.status()!=PaymentStatus.PENDING)throw error(HttpStatus.CONFLICT,"Payment is already terminal");
        return payments.save(p.processed(status));
    }
    public Receipt receipt(String paymentId,UserPrincipal user){
        Payment p=ownedPayment(paymentId,user);
        if(p.status()!=PaymentStatus.COMPLETED)throw error(HttpStatus.CONFLICT,"Receipt requires completed payment");
        var existing=receipts.findById(paymentId);
        if(existing.isPresent())return existing.get();
        Receipt r=new Receipt(p.id(),p.rideId(),p.riderId(),p.amount(),p.currency(),p.transactionReference(),p.paidAt(),Instant.now());
        try{return receipts.insert(r);}
        catch(DuplicateKeyException ex){return receipts.findById(paymentId).orElseThrow(()->error(HttpStatus.CONFLICT,"Receipt is being created"));}
    }
    public Receipt getReceipt(String paymentId,UserPrincipal user){
        Payment p=ownedPayment(paymentId,user);
        if(p.status()!=PaymentStatus.COMPLETED)throw error(HttpStatus.CONFLICT,"Receipt requires completed payment");
        return receipts.findById(paymentId).orElseThrow(()->error(HttpStatus.NOT_FOUND,"Receipt not found"));
    }
    private ResponseStatusException error(HttpStatus status,String message){return new ResponseStatusException(status,message);}
}
