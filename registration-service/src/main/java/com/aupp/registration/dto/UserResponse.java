package com.aupp.registration.dto;

import com.aupp.registration.domain.User;

import java.time.Instant;

public record UserResponse(String id, String email, String role, Instant createdAt) {
    public static UserResponse of(User u) {
        return new UserResponse(u.getId(), u.getEmail(), u.getRole().lower(), u.getCreatedAt());
    }
}
