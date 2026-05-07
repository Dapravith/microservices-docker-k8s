package com.aupp.login.service.impl;

import com.aupp.login.config.JwtProperties;
import com.aupp.login.domain.Role;
import com.aupp.login.domain.User;
import com.aupp.login.dto.LoginRequest;
import com.aupp.login.dto.TokenResponse;
import com.aupp.login.exception.InvalidCredentialsException;
import com.aupp.login.repository.UserRepository;
import com.aupp.login.service.AuthService;
import com.aupp.login.service.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final JwtProperties jwtProps;

    public AuthServiceImpl(UserRepository users, PasswordEncoder encoder, JwtService jwt, JwtProperties jwtProps) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.jwtProps = jwtProps;
    }

    @Override
    public TokenResponse login(LoginRequest req) {
        Role requestedRole = Role.from(req.role());
        User user = users.findByEmail(req.email().toLowerCase())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email, password, or role"));

        if (!encoder.matches(req.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid email, password, or role");
        }
        if (user.getRole() != requestedRole) {
            throw new InvalidCredentialsException("Invalid email, password, or role");
        }

        String token = jwt.issue(user.getEmail(), user.getRole());
        log.info("issued token for {} ({})", user.getEmail(), user.getRole().lower());
        return TokenResponse.bearer(token, jwtProps.expirationSeconds(), user.getRole().lower());
    }
}
