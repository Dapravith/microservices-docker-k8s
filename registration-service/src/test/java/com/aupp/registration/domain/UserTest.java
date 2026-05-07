package com.aupp.registration.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    @Test
    void builderPopulatesAllFields() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        User u = User.builder()
                .id("1").email("a@b.c").passwordHash("h").role(Role.STUDENT).createdAt(now)
                .build();

        assertThat(u.getId()).isEqualTo("1");
        assertThat(u.getEmail()).isEqualTo("a@b.c");
        assertThat(u.getPasswordHash()).isEqualTo("h");
        assertThat(u.getRole()).isEqualTo(Role.STUDENT);
        assertThat(u.getCreatedAt()).isEqualTo(now);
    }

    @Test
    void noArgConstructorAndSettersWork() {
        User u = new User();
        u.setId("x");
        u.setEmail("e");
        u.setPasswordHash("p");
        u.setRole(Role.TEACHER);
        u.setCreatedAt(Instant.EPOCH);

        assertThat(u.getId()).isEqualTo("x");
        assertThat(u.getEmail()).isEqualTo("e");
        assertThat(u.getPasswordHash()).isEqualTo("p");
        assertThat(u.getRole()).isEqualTo(Role.TEACHER);
        assertThat(u.getCreatedAt()).isEqualTo(Instant.EPOCH);
    }
}
