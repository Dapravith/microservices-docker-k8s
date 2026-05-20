package com.aupp.login.controller;

import java.util.Map;

import com.aupp.login.dto.AuthResponse;
import com.aupp.login.dto.LoginRequest;
import com.aupp.login.dto.RegisterRequest;
import com.aupp.login.dto.UserResponse;
import com.aupp.login.model.User;
import com.aupp.login.repository.UserRepository;
import com.aupp.login.service.JwtService;
import jakarta.validation.Valid;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @GetMapping("/health")
    Map<String, String> health() {
        return Map.of("service", "login-service", "status", "UP");
    }

    @PostMapping("/register")
    ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "email already registered");
        }

        User user = new User(
                request.email().toLowerCase(),
                passwordEncoder.encode(request.password()),
                request.role(),
                request.fullName()
        );

        try {
            User saved = userRepository.save(user);
            return ResponseEntity.status(HttpStatus.CREATED).body(new UserResponse(
                    saved.getId(),
                    saved.getEmail(),
                    saved.getRole().name(),
                    saved.getFullName()
            ));
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "email already registered", ex);
        }
    }

    @PostMapping("/login")
    AuthResponse login(@Valid @RequestBody LoginRequest request) {
        User user = userRepository.findByEmail(request.email().toLowerCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid credentials"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid credentials");
        }

        return new AuthResponse(
                jwtService.issue(user),
                "Bearer",
                user.getEmail(),
                user.getRole().name(),
                user.getFullName(),
                jwtService.getTtlSeconds()
        );
    }
}
