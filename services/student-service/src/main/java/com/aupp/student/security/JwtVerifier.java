package com.aupp.student.security;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtVerifier {

    private final SecretKey key;

    public JwtVerifier(@Value("${app.jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public AuthenticatedUser verify(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String email = claims.getSubject();
            String role = claims.get("role", String.class);
            String fullName = claims.get("fullName", String.class);
            if (email == null || email.isBlank() || role == null || role.isBlank()) {
                throw new JwtException("token missing required claims");
            }
            return new AuthenticatedUser(email, role, fullName == null ? "" : fullName);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new InvalidJwtException("invalid or expired token");
        }
    }
}
