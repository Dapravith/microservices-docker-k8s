package com.aupp.login.service.impl;

import com.aupp.login.config.JwtProperties;
import com.aupp.login.domain.Role;
import com.aupp.login.domain.User;
import com.aupp.login.dto.LoginRequest;
import com.aupp.login.dto.RefreshRequest;
import com.aupp.login.dto.TokenResponse;
import com.aupp.login.exception.InvalidCredentialsException;
import com.aupp.login.repository.UserRepository;
import com.aupp.login.service.AuthService;
import com.aupp.login.service.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
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

        String access = jwt.issueAccess(user.getEmail(), user.getRole());
        String refresh = jwt.issueRefresh(user.getEmail(), user.getRole());
        log.info("issued access+refresh tokens for {} ({})", user.getEmail(), user.getRole().lower());
        return TokenResponse.bearer(
                access,
                refresh,
                jwtProps.accessExpirationSeconds(),
                jwtProps.refreshExpirationSeconds(),
                user.getRole().lower());
    }

    @Override
    public TokenResponse refresh(RefreshRequest req) {
        Claims claims;
        try {
            claims = jwt.parseRefresh(req.refreshToken());
        } catch (JwtException ex) {
            log.debug("refresh token rejected: {}", ex.getMessage());
            throw new InvalidCredentialsException("Invalid or expired refresh token");
        }
        String email = claims.get("email", String.class);
        if (email == null) {
            email = claims.getSubject();
        }
        String roleClaim = claims.get("role", String.class);
        if (email == null || roleClaim == null) {
            throw new InvalidCredentialsException("Refresh token missing required claims");
        }
        Role role = Role.from(roleClaim);
        String newAccess = jwt.issueAccess(email, role);
        log.info("refreshed access token for {} ({})", email, role.lower());
        return TokenResponse.bearer(
                newAccess,
                req.refreshToken(),
                jwtProps.accessExpirationSeconds(),
                jwtProps.refreshExpirationSeconds(),
                role.lower());
    }
}
