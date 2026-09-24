package com.ridelink.account.dto;

import com.ridelink.account.entity.Role;

public record UserResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        Role role,
        boolean active
) {
}
