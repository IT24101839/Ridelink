package com.ridelink.drivervehicle.repository;
import com.ridelink.drivervehicle.model.Vehicle;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
public interface VehicleRepository extends MongoRepository<Vehicle,String> {
    List<Vehicle> findByDriverId(String driverId);
    boolean existsByDriverIdAndActiveTrue(String driverId);
}
