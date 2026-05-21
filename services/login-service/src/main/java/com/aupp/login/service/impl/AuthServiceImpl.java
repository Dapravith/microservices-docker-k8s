package com.aupp.login.service.impl;

import java.util.Locale;

import com.aupp.login.dto.AuthResponse;
import com.aupp.login.dto.LoginRequest;
import com.aupp.login.dto.RegisterRequest;
import com.aupp.login.dto.UserResponse;
import com.aupp.login.exception.EmailAlreadyRegisteredException;
import com.aupp.login.exception.InvalidCredentialsException;
import com.aupp.login.model.User;
import com.aupp.login.repository.UserRepository;
import com.aupp.login.service.AuthService;
import com.aupp.login.service.JwtService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Override
    public UserResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException(email);
        }

        User user = new User(
                email,
                passwordEncoder.encode(request.password()),
                request.role(),
                request.fullName().trim()
        );

        try {
            return UserResponse.from(userRepository.save(user));
        } catch (DuplicateKeyException ex) {
            throw new EmailAlreadyRegisteredException(email);
        }
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        User user = userRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
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

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
