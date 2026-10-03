package com.ridelink.drivervehicle.service;

import com.ridelink.drivervehicle.entity.Driver;
import com.ridelink.drivervehicle.entity.DriverStatus;
import com.ridelink.drivervehicle.repository.DriverRepository;
import com.ridelink.drivervehicle.repository.VehicleRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class DriverService {
    private final DriverRepository driverRepository;
    private final VehicleRepository vehicleRepository;
    private final Validator validator;

    public DriverService(DriverRepository driverRepository, VehicleRepository vehicleRepository, Validator validator) {
        this.driverRepository = driverRepository;
        this.vehicleRepository = vehicleRepository;
        this.validator = validator;
    }

    public Driver createDriver(Driver driver) {
        if (driver.getStatus() == null) {
            driver.setStatus(DriverStatus.UNAVAILABLE);
        }
        driver.setEmail(normalizeEmail(driver.getEmail()));
        validate(driver);
        ensureEmailAvailable(driver.getEmail(), null);
        return driverRepository.save(driver);
    }

    public Driver getDriverById(String id) {
        validateId(id);
        return driverRepository.findById(Objects.requireNonNull(id))
            .orElseThrow(() -> new DriverNotFoundException(id));
    }

    public List<Driver> getAllDrivers() {
        return driverRepository.findAll();
    }

    public Driver updateDriver(String id, Driver updatedDriver) {
        Driver existingDriver = getDriverById(id);

        existingDriver.setName(updatedDriver.getName());
        existingDriver.setPhone(updatedDriver.getPhone());
        existingDriver.setEmail(normalizeEmail(updatedDriver.getEmail()));
        existingDriver.setStatus(updatedDriver.getStatus() == null
                ? existingDriver.getStatus() : updatedDriver.getStatus());
        existingDriver.setServiceArea(updatedDriver.getServiceArea());
        existingDriver.setCurrentLatitude(updatedDriver.getCurrentLatitude());
        existingDriver.setCurrentLongitude(updatedDriver.getCurrentLongitude());

        validate(existingDriver);
        ensureEmailAvailable(existingDriver.getEmail(), id);
        return driverRepository.save(existingDriver);
    }

    public Driver updateAvailability(String id, DriverStatus status) {
        Driver driver = getDriverById(id);
        driver.setStatus(status);
        validate(driver);
        return driverRepository.save(driver);
    }

    public Driver updateLocation(String id, Double latitude, Double longitude) {
        Driver driver = getDriverById(id);
        driver.setCurrentLatitude(latitude);
        driver.setCurrentLongitude(longitude);
        validate(driver);
        return driverRepository.save(driver);
    }

    public Driver updateServiceArea(String id, String serviceArea) {
        Driver driver = getDriverById(id);
        driver.setServiceArea(serviceArea);
        validate(driver);
        return driverRepository.save(driver);
    }

    public void deleteDriver(String id) {
        validateId(id);
        Driver driver = getDriverById(id);
        vehicleRepository.deleteAllByDriverId(id);
        driverRepository.delete(Objects.requireNonNull(driver));
    }

    private void ensureEmailAvailable(String email, String currentDriverId) {
        driverRepository.findByEmail(email).ifPresent(existing -> {
            if (!existing.getId().equals(currentDriverId)) {
                throw new DriverEmailAlreadyExistsException(email);
            }
        });
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private void validateId(String id) {
        if (!ObjectId.isValid(id)) {
            throw new InvalidMongoIdException(id);
        }
    }

    private void validate(Driver driver) {
        Set<ConstraintViolation<Driver>> violations = validator.validate(driver);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }
}
