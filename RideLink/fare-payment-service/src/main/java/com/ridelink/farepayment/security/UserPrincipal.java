package com.ridelink.farepayment.security;

public record UserPrincipal(String userId, String role) {
    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}
