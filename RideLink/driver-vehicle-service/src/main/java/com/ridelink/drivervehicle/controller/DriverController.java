package com.ridelink.drivervehicle.controller;
import com.ridelink.drivervehicle.dto.DriverRequests.*;
import com.ridelink.drivervehicle.model.Driver;
import com.ridelink.drivervehicle.security.UserPrincipal;
import com.ridelink.drivervehicle.service.DriverService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;

@RestController @RequestMapping("/api/v1/drivers")
@PreAuthorize("hasAnyRole('DRIVER','ADMIN')")
public class DriverController {
    private final DriverService service;
    public DriverController(DriverService service){this.service=service;}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('DRIVER')")
    public Driver register(@AuthenticationPrincipal UserPrincipal user,@Valid @RequestBody Register r){return service.register(user,r);}
    @GetMapping("/{id}") public Driver get(@PathVariable String id,@AuthenticationPrincipal UserPrincipal user){return service.owned(id,user);}
    @GetMapping("/account/{accountId}") public Driver account(@PathVariable String accountId,@AuthenticationPrincipal UserPrincipal user){return service.ownedAccount(accountId,user);}
    @PatchMapping("/{id}/availability") public Driver availability(@PathVariable String id,@Valid @RequestBody Availability r,@AuthenticationPrincipal UserPrincipal user){return service.availability(id,r,user);}
    @PatchMapping("/{id}/location") public Driver location(@PathVariable String id,@Valid @RequestBody Location r,@AuthenticationPrincipal UserPrincipal user){return service.location(id,r,user);}
    @PatchMapping("/{id}/service-areas") public Driver areas(@PathVariable String id,@Valid @RequestBody Areas r,@AuthenticationPrincipal UserPrincipal user){return service.areas(id,r,user);}
}
