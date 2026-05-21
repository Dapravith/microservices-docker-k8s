package com.aupp.login.dto;

import com.aupp.login.model.User;

public record UserResponse(
        String id,
        String email,
        String role,
        String fullName
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getRole().name(),
                user.getFullName()
        );
    }
}
