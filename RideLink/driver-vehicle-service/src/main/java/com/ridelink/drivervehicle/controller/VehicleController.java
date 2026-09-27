package com.ridelink.drivervehicle.controller;

import com.ridelink.drivervehicle.dto.VehicleRequest;
import com.ridelink.drivervehicle.dto.VehicleResponse;
import com.ridelink.drivervehicle.entity.Vehicle;
import com.ridelink.drivervehicle.service.VehicleService;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/vehicles")
@Tag(name = "Vehicles", description = "Create and manage vehicle records")
public class VehicleController {
    private final VehicleService vehicleService;

    public VehicleController(VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    @PostMapping
    @Operation(summary = "Create a vehicle", description = "Creates a vehicle assigned to an existing driver.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Vehicle created"),
            @ApiResponse(responseCode = "400", description = "Request validation failed"),
            @ApiResponse(responseCode = "404", description = "Driver not found"),
            @ApiResponse(responseCode = "409", description = "Registration number already exists")
    })
    public ResponseEntity<VehicleResponse> createVehicle(@Valid @RequestBody VehicleRequest request) {
        Vehicle created = vehicleService.createVehicle(toEntity(request), request.driverId());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.getId()).toUri();
        return ResponseEntity.created(location).body(toResponse(created));
    }

    @GetMapping
    @Operation(summary = "List vehicles", description = "Returns all vehicles owned by this service.")
    @ApiResponse(responseCode = "200", description = "Vehicle list returned")
    public ResponseEntity<List<VehicleResponse>> getAllVehicles() {
        List<VehicleResponse> vehicles = vehicleService.getAllVehicles().stream()
                .map(VehicleController::toResponse).toList();
        return ResponseEntity.ok(vehicles);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a vehicle", description = "Returns one vehicle by its ID.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Vehicle returned"),
            @ApiResponse(responseCode = "404", description = "Vehicle not found"),
            @ApiResponse(responseCode = "400", description = "Vehicle ID is malformed")
    })
    public ResponseEntity<VehicleResponse> getVehicleById(
            @Parameter(description = "Vehicle ID", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(toResponse(vehicleService.getVehicleById(id)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a vehicle", description = "Updates vehicle details and its existing driver assignment.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Vehicle updated"),
            @ApiResponse(responseCode = "400", description = "Request validation failed or ID is malformed"),
            @ApiResponse(responseCode = "404", description = "Vehicle or driver not found"),
            @ApiResponse(responseCode = "409", description = "Registration number already exists")
    })
    public ResponseEntity<VehicleResponse> updateVehicle(
            @Parameter(description = "Vehicle ID", example = "1") @PathVariable Long id,
            @Valid @RequestBody VehicleRequest request) {
        return ResponseEntity.ok(toResponse(
                vehicleService.updateVehicle(id, toEntity(request), request.driverId())));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a vehicle", description = "Removes a vehicle from its driver's registered vehicles.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Vehicle deleted"),
            @ApiResponse(responseCode = "404", description = "Vehicle not found"),
            @ApiResponse(responseCode = "400", description = "Vehicle ID is malformed")
    })
    public ResponseEntity<Void> deleteVehicle(
            @Parameter(description = "Vehicle ID", example = "1") @PathVariable Long id) {
        vehicleService.deleteVehicle(id);
        return ResponseEntity.noContent().build();
    }

    private static Vehicle toEntity(VehicleRequest request) {
        Vehicle vehicle = new Vehicle();
        vehicle.setRegistrationNumber(request.registrationNumber());
        vehicle.setVehicleType(request.vehicleType());
        vehicle.setModel(request.model());
        vehicle.setStatus(request.status());
        return vehicle;
    }

    private static VehicleResponse toResponse(Vehicle vehicle) {
        return new VehicleResponse(vehicle.getId(), vehicle.getRegistrationNumber(), vehicle.getVehicleType(),
                vehicle.getModel(), vehicle.getStatus(), vehicle.getDriver().getId());
    }
}
