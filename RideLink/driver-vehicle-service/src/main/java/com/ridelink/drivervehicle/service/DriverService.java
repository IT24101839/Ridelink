package com.ridelink.drivervehicle.service;

import com.ridelink.drivervehicle.entity.Driver;
import com.ridelink.drivervehicle.entity.DriverStatus;
import com.ridelink.drivervehicle.repository.DriverRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@Transactional
public class DriverService {
    private final DriverRepository driverRepository;
    private final Validator validator;

    public DriverService(DriverRepository driverRepository, Validator validator) {
        this.driverRepository = driverRepository;
        this.validator = validator;
    }

    public Driver createDriver(Driver driver) {
        if (driver.getStatus() == null) {
            driver.setStatus(DriverStatus.UNAVAILABLE);
        }
        validate(driver);
        ensureEmailAvailable(driver.getEmail(), null);
        return driverRepository.save(driver);
    }

    @Transactional(readOnly = true)
    public Driver getDriverById(Long id) {
        return driverRepository.findById(id).orElseThrow(() -> new DriverNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<Driver> getAllDrivers() {
        return driverRepository.findAll();
    }

    public Driver updateDriver(Long id, Driver updatedDriver) {
        Driver existingDriver = getDriverById(id);

        existingDriver.setName(updatedDriver.getName());
        existingDriver.setPhone(updatedDriver.getPhone());
        existingDriver.setEmail(updatedDriver.getEmail());
        existingDriver.setStatus(updatedDriver.getStatus() == null
                ? existingDriver.getStatus() : updatedDriver.getStatus());
        existingDriver.setServiceArea(updatedDriver.getServiceArea());
        existingDriver.setCurrentLatitude(updatedDriver.getCurrentLatitude());
        existingDriver.setCurrentLongitude(updatedDriver.getCurrentLongitude());

        validate(existingDriver);
        ensureEmailAvailable(existingDriver.getEmail(), id);
        return driverRepository.save(existingDriver);
    }

    public void deleteDriver(Long id) {
        Driver driver = getDriverById(id);
        driverRepository.delete(driver);
    }

    private void ensureEmailAvailable(String email, Long currentDriverId) {
        boolean alreadyExists = currentDriverId == null
                ? driverRepository.existsByEmailIgnoreCase(email)
                : driverRepository.existsByEmailIgnoreCaseAndIdNot(email, currentDriverId);
        if (alreadyExists) {
            throw new DriverEmailAlreadyExistsException(email);
        }
    }

    private void validate(Driver driver) {
        Set<ConstraintViolation<Driver>> violations = validator.validate(driver);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }
}
