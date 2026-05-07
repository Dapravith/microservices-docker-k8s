package com.aupp.login.service;

import com.aupp.login.config.JwtProperties;
import com.aupp.login.domain.Role;
import com.aupp.login.service.impl.JwtServiceImpl;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SECRET = "test-secret-please-change-me-1234567890-abcdef";

    @Test
    void issuedTokenContainsExpectedClaims() {
        JwtService service = new JwtServiceImpl(new JwtProperties(SECRET, "test-issuer", 3600));

        String token = service.issue("alice@itc.edu.kh", Role.STUDENT);

        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();

        assertThat(claims.getSubject()).isEqualTo("alice@itc.edu.kh");
        assertThat(claims.get("role", String.class)).isEqualTo("student");
        assertThat(claims.getIssuer()).isEqualTo("test-issuer");
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    @Test
    void roleClaimIsLowercase() {
        JwtService service = new JwtServiceImpl(new JwtProperties(SECRET, "test-issuer", 3600));
        String token = service.issue("bob@itc.edu.kh", Role.TEACHER);

        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();

        assertThat(claims.get("role", String.class)).isEqualTo("teacher");
    }
}
