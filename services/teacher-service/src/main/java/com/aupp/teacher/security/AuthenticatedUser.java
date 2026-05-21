package com.aupp.teacher.security;

public record AuthenticatedUser(
        String email,
        String role,
        String fullName
) {
}
