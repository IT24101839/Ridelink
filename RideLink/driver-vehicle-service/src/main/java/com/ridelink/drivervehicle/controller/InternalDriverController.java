package com.ridelink.drivervehicle.controller;
import com.ridelink.drivervehicle.dto.DriverRequests.Reservation;
import com.ridelink.drivervehicle.model.Driver;
import com.ridelink.drivervehicle.service.DriverService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.List;
@RestController @RequestMapping("/api/internal/drivers") @PreAuthorize("hasRole('SERVICE')")
public class InternalDriverController {
    private final DriverService service;
    public InternalDriverController(DriverService service){this.service=service;}
    public record Profile(String id,String accountId,String status){
        static Profile from(Driver d){return new Profile(d.getId(),d.getAccountId(),d.getStatus().name());}
    }
    @GetMapping("/available") public List<Profile> available(){return service.available().stream().map(Profile::from).toList();}
    @GetMapping("/{id}") public Profile get(@PathVariable String id){return Profile.from(service.get(id));}
    @GetMapping("/account/{accountId}") public Profile account(@PathVariable String accountId){return Profile.from(service.byAccount(accountId));}
    @PutMapping("/{id}/reservation") public Profile reserve(@PathVariable String id,@Valid @RequestBody Reservation r){return Profile.from(service.reserve(id,r.rideId()));}
    @DeleteMapping("/{id}/reservation/{rideId}") public Profile release(@PathVariable String id,@PathVariable String rideId){return Profile.from(service.release(id,rideId));}
}
