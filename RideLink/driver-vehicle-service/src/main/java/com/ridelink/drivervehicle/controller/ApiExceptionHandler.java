package com.ridelink.drivervehicle.controller;

import com.ridelink.drivervehicle.service.DriverEmailAlreadyExistsException;
import com.ridelink.drivervehicle.service.DriverNotFoundException;
import com.ridelink.drivervehicle.service.VehicleNotFoundException;
import com.ridelink.drivervehicle.service.VehicleRegistrationAlreadyExistsException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler({DriverNotFoundException.class, VehicleNotFoundException.class})
    public ProblemDetail handleNotFound(RuntimeException exception) {
        return problem(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler({DriverEmailAlreadyExistsException.class, VehicleRegistrationAlreadyExistsException.class,
            DataIntegrityViolationException.class})
    public ProblemDetail handleConflict(RuntimeException exception) {
        return problem(HttpStatus.CONFLICT, exception instanceof DataIntegrityViolationException
                ? "A record with one of the unique values already exists" : exception.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class,
            HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ProblemDetail handleBadRequest(Exception exception) {
        String detail = exception instanceof MethodArgumentNotValidException validationException
                ? validationException.getBindingResult().getFieldErrors().stream()
                    .map(error -> error.getField() + ": " + error.getDefaultMessage())
                    .findFirst().orElse("Request validation failed")
                : exception instanceof ConstraintViolationException violationException
                    ? violationException.getConstraintViolations().stream()
                        .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                        .findFirst().orElse("Request validation failed")
                    : "Request contains an invalid value";
        return problem(HttpStatus.BAD_REQUEST, detail);
    }

    private static ProblemDetail problem(HttpStatus status, String detail) {
        return ProblemDetail.forStatusAndDetail(status, detail);
    }
}
