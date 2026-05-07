package com.aupp.registration.controller;

import com.aupp.registration.domain.Role;
import com.aupp.registration.dto.RegisterRequest;
import com.aupp.registration.dto.RoleScopedRegisterRequest;
import com.aupp.registration.dto.UserResponse;
import com.aupp.registration.service.RegistrationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/register")
public class RegistrationController {

    private final RegistrationService service;

    public RegistrationController(RegistrationService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.register(req));
    }

    @PostMapping("/student")
    public ResponseEntity<UserResponse> registerStudent(@Valid @RequestBody RoleScopedRegisterRequest req) {
        UserResponse created = service.registerWithRole(req.email(), req.password(), Role.STUDENT);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/teacher")
    public ResponseEntity<UserResponse> registerTeacher(@Valid @RequestBody RoleScopedRegisterRequest req) {
        UserResponse created = service.registerWithRole(req.email(), req.password(), Role.TEACHER);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
