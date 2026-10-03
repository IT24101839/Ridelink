package com.ridelink.drivervehicle.repository;
import com.ridelink.drivervehicle.model.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface VehicleRepository extends JpaRepository<Vehicle,String> {
    List<Vehicle> findByDriverId(String driverId);
    boolean existsByDriverIdAndActiveTrue(String driverId);
}
