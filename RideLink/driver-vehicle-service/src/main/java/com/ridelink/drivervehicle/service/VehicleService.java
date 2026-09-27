package com.ridelink.drivervehicle.service;

import com.ridelink.drivervehicle.entity.Driver;
import com.ridelink.drivervehicle.entity.Vehicle;
import com.ridelink.drivervehicle.entity.VehicleStatus;
import com.ridelink.drivervehicle.repository.DriverRepository;
import com.ridelink.drivervehicle.repository.VehicleRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@Transactional
public class VehicleService {
    private final VehicleRepository vehicleRepository;
    private final DriverRepository driverRepository;
    private final Validator validator;

    public VehicleService(VehicleRepository vehicleRepository, DriverRepository driverRepository,
                          Validator validator) {
        this.vehicleRepository = vehicleRepository;
        this.driverRepository = driverRepository;
        this.validator = validator;
    }

    public Vehicle createVehicle(Vehicle vehicle, Long driverId) {
        Driver driver = findDriver(driverId);
        driver.addVehicle(vehicle);
        if (vehicle.getStatus() == null) {
            vehicle.setStatus(VehicleStatus.ACTIVE);
        }
        validate(vehicle);
        ensureRegistrationAvailable(vehicle.getRegistrationNumber(), null);
        return vehicleRepository.save(vehicle);
    }

    @Transactional(readOnly = true)
    public Vehicle getVehicleById(Long id) {
        return vehicleRepository.findById(id).orElseThrow(() -> new VehicleNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<Vehicle> getAllVehicles() {
        return vehicleRepository.findAll();
    }

    public Vehicle updateVehicle(Long id, Vehicle updatedVehicle, Long driverId) {
        Vehicle existingVehicle = getVehicleById(id);
        Driver driver = findDriver(driverId);

        existingVehicle.setRegistrationNumber(updatedVehicle.getRegistrationNumber());
        existingVehicle.setVehicleType(updatedVehicle.getVehicleType());
        existingVehicle.setModel(updatedVehicle.getModel());
        existingVehicle.setStatus(updatedVehicle.getStatus() == null
                ? existingVehicle.getStatus() : updatedVehicle.getStatus());
        if (!existingVehicle.getDriver().getId().equals(driver.getId())) {
            existingVehicle.getDriver().removeVehicle(existingVehicle);
            driver.addVehicle(existingVehicle);
        }

        validate(existingVehicle);
        ensureRegistrationAvailable(existingVehicle.getRegistrationNumber(), id);
        return vehicleRepository.save(existingVehicle);
    }

    public void deleteVehicle(Long id) {
        Vehicle vehicle = getVehicleById(id);
        vehicle.getDriver().removeVehicle(vehicle);
    }

    private Driver findDriver(Long driverId) {
        return driverRepository.findById(driverId).orElseThrow(() -> new DriverNotFoundException(driverId));
    }

    private void ensureRegistrationAvailable(String registrationNumber, Long currentVehicleId) {
        boolean alreadyExists = currentVehicleId == null
                ? vehicleRepository.existsByRegistrationNumberIgnoreCase(registrationNumber)
                : vehicleRepository.existsByRegistrationNumberIgnoreCaseAndIdNot(registrationNumber, currentVehicleId);
        if (alreadyExists) {
            throw new VehicleRegistrationAlreadyExistsException(registrationNumber);
        }
    }

    private void validate(Vehicle vehicle) {
        Set<ConstraintViolation<Vehicle>> violations = validator.validate(vehicle);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }
}
