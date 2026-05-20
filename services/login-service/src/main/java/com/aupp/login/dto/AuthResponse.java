package com.aupp.login.dto;

public record AuthResponse(
        String token,
        String tokenType,
        String email,
        String role,
        String fullName,
        long expiresInSeconds
) {
}
