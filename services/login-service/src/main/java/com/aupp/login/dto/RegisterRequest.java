package com.aupp.login.dto;

import com.aupp.login.model.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @Email @NotBlank String email,
        @NotBlank @Size(min = 6, max = 80) String password,
        @NotNull Role role,
        @NotBlank @Size(max = 120) String fullName
) {
}
