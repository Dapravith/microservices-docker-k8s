package com.aupp.login.dto;

import com.aupp.login.domain.User;

public record UserResponse(String id, String email, String role) {
    public static UserResponse of(User u) {
        return new UserResponse(u.getId(), u.getEmail(), u.getRole().lower());
    }
}
