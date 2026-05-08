package com.aupp.gateway.security;

import com.aupp.gateway.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtVerifierTest {

    private static final String SECRET = "test-secret-please-change-me-1234567890-abcdef";

    @Test
    void verifyParsesValidTokenAndReturnsClaims() {
        JwtVerifier verifier = new JwtVerifier(new JwtProperties(SECRET));
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        String token = Jwts.builder()
                .subject("alice@x.y")
                .claim("email", "alice@x.y")
                .claim("role", "student")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 60_000))
                .signWith(key)
                .compact();

        Claims claims = verifier.verify(token);

        assertThat(claims.getSubject()).isEqualTo("alice@x.y");
        assertThat(claims.get("role", String.class)).isEqualTo("student");
    }

    @Test
    void verifyRejectsTamperedSignature() {
        JwtVerifier verifier = new JwtVerifier(new JwtProperties(SECRET));
        SecretKey otherKey = Keys.hmacShaKeyFor("different-secret-of-at-least-32-characters!!".getBytes(StandardCharsets.UTF_8));
        String forgedToken = Jwts.builder()
                .subject("a@b.c")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(otherKey)
                .compact();

        assertThatThrownBy(() -> verifier.verify(forgedToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void verifyRejectsExpiredToken() {
        JwtVerifier verifier = new JwtVerifier(new JwtProperties(SECRET));
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        String expired = Jwts.builder()
                .subject("a@b.c")
                .issuedAt(new Date(now.getTime() - 120_000))
                .expiration(new Date(now.getTime() - 60_000))
                .signWith(key)
                .compact();

        assertThatThrownBy(() -> verifier.verify(expired))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void verifyRejectsMalformedToken() {
        JwtVerifier verifier = new JwtVerifier(new JwtProperties(SECRET));

        assertThatThrownBy(() -> verifier.verify("not-a-real-jwt"))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void shortSecretIsPaddedToMinimumLength() {
        // The verifier accepts a short secret by zero-padding to 32 bytes.
        // Tokens issued with the same padded key must round-trip.
        JwtVerifier verifier = new JwtVerifier(new JwtProperties("short"));

        byte[] padded = new byte[32];
        byte[] raw = "short".getBytes(StandardCharsets.UTF_8);
        System.arraycopy(raw, 0, padded, 0, raw.length);
        SecretKey key = Keys.hmacShaKeyFor(padded);

        String token = Jwts.builder()
                .subject("a@b.c")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key)
                .compact();

        assertThat(verifier.verify(token).getSubject()).isEqualTo("a@b.c");
    }
}
