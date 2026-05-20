package com.aupp.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank @Email
    private String email;

    @NotBlank @Size(min = 6)
    private String password;

    @NotBlank
    @Pattern(regexp = "STUDENT|TEACHER", message = "role must be STUDENT or TEACHER")
    private String role;

    @NotBlank
    private String fullName;
}
