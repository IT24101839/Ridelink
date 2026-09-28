package com.ridelink.drivervehicle.service;

import com.ridelink.drivervehicle.entity.Driver;
import com.ridelink.drivervehicle.entity.Vehicle;
import com.ridelink.drivervehicle.entity.VehicleStatus;
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

    public Vehicle createVehicle(Vehicle vehicle, String driverId) {
        findDriver(driverId);
        vehicle.setDriverId(driverId);
        if (vehicle.getStatus() == null) {
            vehicle.setStatus(VehicleStatus.ACTIVE);
        }
        vehicle.setRegistrationNumber(normalizeRegistration(vehicle.getRegistrationNumber()));
        validate(vehicle);
        ensureRegistrationAvailable(vehicle.getRegistrationNumber(), null);
        return vehicleRepository.save(vehicle);
    }

    public Vehicle getVehicleById(String id) {
        validateId(id);
        return vehicleRepository.findById(Objects.requireNonNull(id))
            .orElseThrow(() -> new VehicleNotFoundException(id));
    }

    public List<Vehicle> getAllVehicles() {
        return vehicleRepository.findAll();
    }

    public Vehicle updateVehicle(String id, Vehicle updatedVehicle, String driverId) {
        Vehicle existingVehicle = getVehicleById(id);
        findDriver(driverId);

        existingVehicle.setRegistrationNumber(normalizeRegistration(updatedVehicle.getRegistrationNumber()));
        existingVehicle.setVehicleType(updatedVehicle.getVehicleType());
        existingVehicle.setModel(updatedVehicle.getModel());
        existingVehicle.setStatus(updatedVehicle.getStatus() == null
                ? existingVehicle.getStatus() : updatedVehicle.getStatus());
        existingVehicle.setDriverId(driverId);

        validate(existingVehicle);
        ensureRegistrationAvailable(existingVehicle.getRegistrationNumber(), id);
        return vehicleRepository.save(existingVehicle);
    }

    public void deleteVehicle(String id) {
        validateId(id);
        Vehicle vehicle = getVehicleById(id);
        vehicleRepository.delete(Objects.requireNonNull(vehicle));
    }

    private Driver findDriver(String driverId) {
        validateId(driverId);
        return driverRepository.findById(Objects.requireNonNull(driverId))
            .orElseThrow(() -> new DriverNotFoundException(driverId));
    }

    private void validateId(String id) {
        if (!ObjectId.isValid(id)) {
            throw new InvalidMongoIdException(id);
        }
    }

    private void ensureRegistrationAvailable(String registrationNumber, String currentVehicleId) {
        vehicleRepository.findByRegistrationNumber(registrationNumber).ifPresent(existing -> {
            if (!existing.getId().equals(currentVehicleId)) {
                throw new VehicleRegistrationAlreadyExistsException(registrationNumber);
            }
        });
    }

    private String normalizeRegistration(String registrationNumber) {
        return registrationNumber == null ? null : registrationNumber.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private void validate(Vehicle vehicle) {
        Set<ConstraintViolation<Vehicle>> violations = validator.validate(vehicle);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }
}
