package com.ridelink.account.controller;

import com.ridelink.account.dto.RegisterRequest;
import com.ridelink.account.dto.UserResponse;
import com.ridelink.account.entity.User;
import com.ridelink.account.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(
            @Valid @RequestBody RegisterRequest request) {

        User user = authService.register(request);

        UserResponse response = new UserResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getRole(),
                user.isActive()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
}