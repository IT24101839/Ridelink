package com.ridelink.account.dto;

import com.ridelink.account.entity.Role;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RoleUpdateRequest {

    @NotNull(message = "role is required")
    private Role role;
}
