package com.aupp.registration.service.impl;

import com.aupp.registration.domain.Role;
import com.aupp.registration.domain.User;
import com.aupp.registration.dto.RegisterRequest;
import com.aupp.registration.dto.UserResponse;
import com.aupp.registration.exception.UserAlreadyExistsException;
import com.aupp.registration.repository.UserRepository;
import com.aupp.registration.service.RegistrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class RegistrationServiceImpl implements RegistrationService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationServiceImpl.class);

    private final UserRepository users;
    private final PasswordEncoder encoder;

    public RegistrationServiceImpl(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @Override
    public UserResponse register(RegisterRequest req) {
        return registerInternal(req.email(), req.password(), Role.from(req.role()));
    }

    @Override
    public UserResponse registerWithRole(String email, String password, Role role) {
        return registerInternal(email, password, role);
    }

    private UserResponse registerInternal(String rawEmail, String rawPassword, Role role) {
        String email = rawEmail.trim().toLowerCase();

        if (users.existsByEmail(email)) {
            throw new UserAlreadyExistsException("User with email " + email + " already exists");
        }

        User user = User.builder()
                .email(email)
                .passwordHash(encoder.encode(rawPassword))
                .role(role)
                .build();

        try {
            User saved = users.save(user);
            log.info("registered user {} ({})", saved.getEmail(), saved.getRole().lower());
            return UserResponse.of(saved);
        } catch (DuplicateKeyException dup) {
            throw new UserAlreadyExistsException("User with email " + email + " already exists");
        }
    }
}
