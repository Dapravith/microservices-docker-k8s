package com.aupp.login.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import com.aupp.login.model.Role;
import com.aupp.login.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "test-secret-key-with-at-least-32-bytes-for-hs256";

    @Test
    void issuesTokenWithSubjectAndRole() {
        JwtService jwtService = new JwtService(SECRET, 3600);
        User user = new User("student1@aupp.edu", "hash", Role.STUDENT, "Student One");

        String token = jwtService.issue(user);

        Claims claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertThat(claims.getSubject()).isEqualTo("student1@aupp.edu");
        assertThat(claims.get("role", String.class)).isEqualTo("STUDENT");
        assertThat(claims.get("fullName", String.class)).isEqualTo("Student One");
    }
}
