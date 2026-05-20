package com.aupp.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

class JwtVerifierTest {

    private static final String SECRET = "test-secret-key-with-at-least-32-bytes-for-hs256";

    @Test
    void verifiesSignedJwtAndExtractsGatewayIdentity() {
        String token = Jwts.builder()
                .subject("teacher1@aupp.edu")
                .claim("role", "TEACHER")
                .claim("fullName", "Teacher One")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();

        AuthenticatedUser user = new JwtVerifier(SECRET).verify(token);

        assertThat(user.email()).isEqualTo("teacher1@aupp.edu");
        assertThat(user.role()).isEqualTo("TEACHER");
        assertThat(user.fullName()).isEqualTo("Teacher One");
    }
}
