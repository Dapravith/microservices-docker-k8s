package com.aupp.teacher.web;

public record CallerIdentity(String email, String role) {

    public static final String EMAIL_HEADER = "X-User-Email";
    public static final String ROLE_HEADER = "X-User-Role";

    public boolean isTeacher() {
        return "teacher".equalsIgnoreCase(role);
    }
}
