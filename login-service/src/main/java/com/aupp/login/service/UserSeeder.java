package com.aupp.login.service;

import com.aupp.login.domain.Role;
import com.aupp.login.domain.User;
import com.aupp.login.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class UserSeeder {

    private static final Logger log = LoggerFactory.getLogger(UserSeeder.class);

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final boolean seedEnabled;

    public UserSeeder(UserRepository users,
                      PasswordEncoder encoder,
                      @Value("${app.seed.enabled:true}") boolean seedEnabled) {
        this.users = users;
        this.encoder = encoder;
        this.seedEnabled = seedEnabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        if (!seedEnabled) {
            log.info("user seeding disabled");
            return;
        }
        List<SeedUser> seeds = List.of(
                new SeedUser("student1@itc.edu.kh", "student123", Role.STUDENT),
                new SeedUser("teacher1@itc.edu.kh", "teacher123", Role.TEACHER)
        );
        for (SeedUser s : seeds) {
            if (users.existsByEmail(s.email)) {
                log.info("seed: {} already exists, skipping", s.email);
                continue;
            }
            users.save(User.builder()
                    .email(s.email)
                    .passwordHash(encoder.encode(s.password))
                    .role(s.role)
                    .build());
            log.info("seed: created {} ({})", s.email, s.role.lower());
        }
    }

    private record SeedUser(String email, String password, Role role) {}
}
