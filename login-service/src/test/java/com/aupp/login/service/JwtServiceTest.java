package com.aupp.login.service;

import com.aupp.login.config.JwtProperties;
import com.aupp.login.domain.Role;
import com.aupp.login.service.impl.JwtServiceImpl;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "test-secret-please-change-me-1234567890-abcdef";

    private JwtService newService() {
        return new JwtServiceImpl(new JwtProperties(SECRET, "test-issuer", 900, 604_800));
    }

    private Claims read(String token) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    @Test
    void accessTokenContainsExpectedClaimsIncludingTypAccess() {
        String token = newService().issueAccess("alice@itc.edu.kh", Role.STUDENT);
        Claims claims = read(token);

        assertThat(claims.getSubject()).isEqualTo("alice@itc.edu.kh");
        assertThat(claims.get("role", String.class)).isEqualTo("student");
        assertThat(claims.get("typ", String.class)).isEqualTo("access");
        assertThat(claims.getIssuer()).isEqualTo("test-issuer");
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    @Test
    void refreshTokenIsTaggedTypRefreshAndLivesLongerThanAccess() {
        JwtService service = newService();
        String access = service.issueAccess("bob@x.y", Role.TEACHER);
        String refresh = service.issueRefresh("bob@x.y", Role.TEACHER);

        Claims accessClaims = read(access);
        Claims refreshClaims = read(refresh);

        assertThat(refreshClaims.get("typ", String.class)).isEqualTo("refresh");
        assertThat(refreshClaims.get("role", String.class)).isEqualTo("teacher");
        assertThat(refreshClaims.getExpiration()).isAfter(accessClaims.getExpiration());
    }

    @Test
    void roleClaimIsLowercase() {
        Claims claims = read(newService().issueAccess("bob@itc.edu.kh", Role.TEACHER));
        assertThat(claims.get("role", String.class)).isEqualTo("teacher");
    }

    @Test
    void parseRefreshAcceptsRefreshTokens() {
        JwtService service = newService();
        String refresh = service.issueRefresh("alice@itc.edu.kh", Role.STUDENT);

        Claims claims = service.parseRefresh(refresh);

        assertThat(claims.get("email", String.class)).isEqualTo("alice@itc.edu.kh");
        assertThat(claims.get("role", String.class)).isEqualTo("student");
    }

    @Test
    void parseRefreshRejectsAccessTokens() {
        JwtService service = newService();
        String access = service.issueAccess("alice@itc.edu.kh", Role.STUDENT);

        assertThatThrownBy(() -> service.parseRefresh(access))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("expected 'refresh'");
    }

    @Test
    void parseRefreshRejectsTamperedTokens() {
        assertThatThrownBy(() -> newService().parseRefresh("not-a-real-jwt"))
                .isInstanceOf(JwtException.class);
    }
}
