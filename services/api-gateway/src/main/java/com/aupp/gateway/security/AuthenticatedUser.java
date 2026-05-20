package com.aupp.gateway.security;

public record AuthenticatedUser(
        String email,
        String role,
        String fullName
) {
}
