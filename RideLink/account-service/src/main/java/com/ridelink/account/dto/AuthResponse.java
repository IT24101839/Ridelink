package com.ridelink.account.dto;

import com.ridelink.account.entity.Role;

public record AuthResponse(
        String token,
        Long userId,
        String email,
        String firstName,
        String lastName,
        Role role
) {
}
