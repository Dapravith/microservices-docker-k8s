package com.aupp.login.controller;

import com.aupp.login.dto.LoginRequest;
import com.aupp.login.dto.RegisterRequest;
import com.aupp.login.dto.TokenResponse;
import com.aupp.login.dto.UserResponse;
import com.aupp.login.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest req) {
        return auth.login(req);
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest req) {
        UserResponse created = auth.register(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
