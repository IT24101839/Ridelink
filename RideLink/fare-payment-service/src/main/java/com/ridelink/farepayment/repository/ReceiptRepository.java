package com.ridelink.farepayment.repository;
import com.ridelink.farepayment.model.Receipt;
import org.springframework.data.mongodb.repository.MongoRepository;
public interface ReceiptRepository extends MongoRepository<Receipt,String> {}
