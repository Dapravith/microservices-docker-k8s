package com.aupp.login.dto;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long accessExpiresInSeconds,
        long refreshExpiresInSeconds,
        String role
) {
    public static TokenResponse bearer(
            String accessToken,
            String refreshToken,
            long accessExpiresInSeconds,
            long refreshExpiresInSeconds,
            String role
    ) {
        return new TokenResponse(accessToken, refreshToken, "Bearer",
                accessExpiresInSeconds, refreshExpiresInSeconds, role);
    }
}
