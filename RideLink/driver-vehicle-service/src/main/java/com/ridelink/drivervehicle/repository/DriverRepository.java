package com.ridelink.drivervehicle.repository;
import com.ridelink.drivervehicle.model.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface DriverRepository extends JpaRepository<Driver,String> {
    Optional<Driver> findByAccountId(String accountId);
    List<Driver> findByStatusAndReservedRideIdIsNull(DriverStatus status);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Driver d where d.id = :id")
    Optional<Driver> lockById(@Param("id") String id);
}
