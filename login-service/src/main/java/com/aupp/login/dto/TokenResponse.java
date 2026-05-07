package com.aupp.login.dto;

public record TokenResponse(String token, String tokenType, long expiresInSeconds, String role) {
    public static TokenResponse bearer(String token, long expiresInSeconds, String role) {
        return new TokenResponse(token, "Bearer", expiresInSeconds, role);
    }
}
