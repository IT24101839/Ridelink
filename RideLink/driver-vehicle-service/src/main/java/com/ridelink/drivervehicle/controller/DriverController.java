package com.ridelink.drivervehicle.controller;

import com.ridelink.drivervehicle.dto.DriverRequest;
import com.ridelink.drivervehicle.dto.DriverResponse;
import com.ridelink.drivervehicle.dto.AvailabilityUpdateRequest;
import com.ridelink.drivervehicle.dto.LocationUpdateRequest;
import com.ridelink.drivervehicle.dto.ServiceAreaUpdateRequest;
import com.ridelink.drivervehicle.entity.Driver;
import com.ridelink.drivervehicle.service.DriverService;
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
@RequestMapping("/api/drivers")
@Tag(name = "Drivers", description = "Create and manage driver records")
public class DriverController {
    private final DriverService driverService;

    public DriverController(DriverService driverService) {
        this.driverService = driverService;
    }

    @PostMapping
    @Operation(summary = "Create a driver", description = "Creates a driver after validating the supplied details.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Driver created"),
            @ApiResponse(responseCode = "400", description = "Request validation failed"),
            @ApiResponse(responseCode = "409", description = "Email already exists")
    })
    public ResponseEntity<DriverResponse> createDriver(@Valid @RequestBody DriverRequest request) {
        Driver created = driverService.createDriver(toEntity(request));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.getId()).toUri();
        return ResponseEntity.created(location).body(toResponse(created));
    }

    @GetMapping
    @Operation(summary = "List drivers", description = "Returns all drivers owned by this service.")
    @ApiResponse(responseCode = "200", description = "Driver list returned")
    public ResponseEntity<List<DriverResponse>> getAllDrivers() {
        List<DriverResponse> drivers = driverService.getAllDrivers().stream()
                .map(DriverController::toResponse).toList();
        return ResponseEntity.ok(drivers);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a driver", description = "Returns one driver by its ID.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Driver returned"),
            @ApiResponse(responseCode = "404", description = "Driver not found"),
            @ApiResponse(responseCode = "400", description = "Driver ID is malformed")
    })
    public ResponseEntity<DriverResponse> getDriverById(
            @Parameter(description = "MongoDB ObjectId of the driver", example = "66a1b2c3d4e5f60718293a4b") @PathVariable String id) {
        return ResponseEntity.ok(toResponse(driverService.getDriverById(id)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a driver", description = "Replaces the driver's editable profile fields.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Driver updated"),
            @ApiResponse(responseCode = "400", description = "Request validation failed or ID is malformed"),
            @ApiResponse(responseCode = "404", description = "Driver not found"),
            @ApiResponse(responseCode = "409", description = "Email already exists")
    })
    public ResponseEntity<DriverResponse> updateDriver(
            @Parameter(description = "MongoDB ObjectId of the driver", example = "66a1b2c3d4e5f60718293a4b") @PathVariable String id,
            @Valid @RequestBody DriverRequest request) {
        return ResponseEntity.ok(toResponse(driverService.updateDriver(id, toEntity(request))));
    }

        @PatchMapping("/{id}/availability")
        @Operation(summary = "Update driver availability", description = "Changes the driver's availability status.")
        @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Driver availability updated"),
            @ApiResponse(responseCode = "400", description = "Invalid status or driver ID"),
            @ApiResponse(responseCode = "404", description = "Driver not found")
        })
        public ResponseEntity<DriverResponse> updateAvailability(
            @Parameter(description = "MongoDB ObjectId of the driver", example = "66a1b2c3d4e5f60718293a4b") @PathVariable String id,
            @Valid @RequestBody AvailabilityUpdateRequest request) {
        return ResponseEntity.ok(toResponse(driverService.updateAvailability(id, request.status())));
        }

        @PatchMapping("/{id}/location")
        @Operation(summary = "Update driver location", description = "Updates the driver's current coordinates.")
        @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Driver location updated"),
            @ApiResponse(responseCode = "400", description = "Invalid coordinates or driver ID"),
            @ApiResponse(responseCode = "404", description = "Driver not found")
        })
        public ResponseEntity<DriverResponse> updateLocation(
            @Parameter(description = "MongoDB ObjectId of the driver", example = "66a1b2c3d4e5f60718293a4b") @PathVariable String id,
            @Valid @RequestBody LocationUpdateRequest request) {
        return ResponseEntity.ok(toResponse(driverService.updateLocation(
            id, request.latitude(), request.longitude())));
        }

        @PatchMapping("/{id}/service-area")
        @Operation(summary = "Update driver service area", description = "Changes the area in which the driver provides service.")
        @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Driver service area updated"),
            @ApiResponse(responseCode = "400", description = "Invalid service area or driver ID"),
            @ApiResponse(responseCode = "404", description = "Driver not found")
        })
        public ResponseEntity<DriverResponse> updateServiceArea(
            @Parameter(description = "MongoDB ObjectId of the driver", example = "66a1b2c3d4e5f60718293a4b") @PathVariable String id,
            @Valid @RequestBody ServiceAreaUpdateRequest request) {
        return ResponseEntity.ok(toResponse(driverService.updateServiceArea(id, request.serviceArea())));
        }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a driver", description = "Deletes the driver and its registered vehicles.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Driver deleted"),
            @ApiResponse(responseCode = "404", description = "Driver not found"),
            @ApiResponse(responseCode = "400", description = "Driver ID is malformed")
    })
    public ResponseEntity<Void> deleteDriver(
            @Parameter(description = "MongoDB ObjectId of the driver", example = "66a1b2c3d4e5f60718293a4b") @PathVariable String id) {
        driverService.deleteDriver(id);
        return ResponseEntity.noContent().build();
    }

    private static Driver toEntity(DriverRequest request) {
        Driver driver = new Driver();
        driver.setName(request.name());
        driver.setPhone(request.phone());
        driver.setEmail(request.email());
        driver.setStatus(request.status());
        driver.setServiceArea(request.serviceArea());
        driver.setCurrentLatitude(request.currentLatitude());
        driver.setCurrentLongitude(request.currentLongitude());
        return driver;
    }

    private static DriverResponse toResponse(Driver driver) {
        return new DriverResponse(driver.getId(), driver.getName(), driver.getPhone(), driver.getEmail(),
                driver.getStatus(), driver.getServiceArea(), driver.getCurrentLatitude(), driver.getCurrentLongitude());
    }
}
