package com.aupp.student.security;

public record AuthenticatedUser(
        String email,
        String role,
        String fullName
) {
}
