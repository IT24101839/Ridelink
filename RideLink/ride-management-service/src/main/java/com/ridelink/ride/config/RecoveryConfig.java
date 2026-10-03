package com.ridelink.ride.config;
import com.ridelink.ride.service.RideService;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;
@Configuration @EnableScheduling
public class RecoveryConfig {
    private final RideService rides;
    public RecoveryConfig(RideService rides){this.rides=rides;}
    @Scheduled(fixedDelayString="${ride.recovery-delay-ms:10000}",initialDelayString="${ride.recovery-delay-ms:10000}")
    public void recover(){
        try{rides.recoverPendingOperations();}
        catch(RuntimeException ex){org.slf4j.LoggerFactory.getLogger(RecoveryConfig.class).warn("Ride recovery delayed: database unavailable");}
    }
}
