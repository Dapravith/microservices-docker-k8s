package com.aupp.login.dto;

public record UserResponse(
        String id,
        String email,
        String role,
        String fullName
) {
}
