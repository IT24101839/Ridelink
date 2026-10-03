package com.ridelink.drivervehicle.security;

public record UserPrincipal(String userId, String role) {
    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}
