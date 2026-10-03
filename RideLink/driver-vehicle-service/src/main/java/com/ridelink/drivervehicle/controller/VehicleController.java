package com.ridelink.drivervehicle.controller;
import com.ridelink.drivervehicle.dto.DriverRequests.VehicleRequest;
import com.ridelink.drivervehicle.model.Vehicle;
import com.ridelink.drivervehicle.security.UserPrincipal;
import com.ridelink.drivervehicle.service.DriverService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import java.util.List;
@RestController @RequestMapping("/api/v1/vehicles") @PreAuthorize("hasAnyRole('DRIVER','ADMIN')")
@SecurityRequirement(name = "bearerAuth")
public class VehicleController {
    private final DriverService service;
    public VehicleController(DriverService service){this.service=service;}
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public Vehicle create(@Valid @RequestBody VehicleRequest r,@AuthenticationPrincipal UserPrincipal user){return service.addVehicle(r,user);}
    @GetMapping("/driver/{driverId}")
    public List<Vehicle> get(@PathVariable String driverId,@AuthenticationPrincipal UserPrincipal user){return service.vehicles(driverId,user);}
}
