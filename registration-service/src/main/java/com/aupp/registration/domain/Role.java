package com.aupp.registration.domain;

public enum Role {
    STUDENT,
    TEACHER;

    public static Role from(String value) {
        if (value == null) {
            throw new IllegalArgumentException("role is required");
        }
        return Role.valueOf(value.trim().toUpperCase());
    }

    public String lower() {
        return name().toLowerCase();
    }
}
