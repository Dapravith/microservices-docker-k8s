package com.aupp.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class JwtRoleAuthFilterTest {

    private static final String SECRET = "test-secret-key-with-at-least-32-bytes-for-hs256";
    private final JwtRoleAuthFilter filter = new JwtRoleAuthFilter(new JwtVerifier(SECRET));

    @Test
    void mapsStudentAndTeacherPathsToRequiredRoles() {
        assertThat(filter.requiredRole("/student")).isEqualTo("STUDENT");
        assertThat(filter.requiredRole("/student/tasks")).isEqualTo("STUDENT");
        assertThat(filter.requiredRole("/student/submissions")).isEqualTo("STUDENT");
        assertThat(filter.requiredRole("/teacher")).isEqualTo("TEACHER");
        assertThat(filter.requiredRole("/teacher/tasks")).isEqualTo("TEACHER");
        assertThat(filter.requiredRole("/unknown")).isNull();
    }

    @Test
    void leavesAuthAndActuatorPublic() {
        assertThat(filter.isPublicPath("/auth/login")).isTrue();
        assertThat(filter.isPublicPath("/actuator/health")).isTrue();
        assertThat(filter.isPublicPath("/student")).isFalse();
    }

    @Test
    void blocksTeacherTokenFromStudentActivityEndpoints() {
        assertForbidden("/student", token("teacher1@aupp.edu", "TEACHER"));
        assertForbidden("/student/tasks", token("teacher1@aupp.edu", "TEACHER"));
        assertForbidden("/student/submissions", token("teacher1@aupp.edu", "TEACHER"));
    }

    @Test
    void blocksStudentTokenFromTeacherActivityEndpoints() {
        assertForbidden("/teacher", token("student1@aupp.edu", "STUDENT"));
        assertForbidden("/teacher/tasks", token("student1@aupp.edu", "STUDENT"));
    }

    @Test
    void injectsGatewayIdentityForAllowedRole() {
        MockServerWebExchange exchange = exchange("/student/tasks", token("student1@aupp.edu", "STUDENT"));
        AtomicReference<ServerHttpRequest> routedRequest = new AtomicReference<>();
        GatewayFilterChain chain = routedExchange -> {
            routedRequest.set(routedExchange.getRequest());
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isNull();
        assertThat(routedRequest.get().getHeaders().getFirst("X-User-Email")).isEqualTo("student1@aupp.edu");
        assertThat(routedRequest.get().getHeaders().getFirst("X-User-Role")).isEqualTo("STUDENT");
    }

    private void assertForbidden(String path, String token) {
        MockServerWebExchange exchange = exchange(path, token);
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = routedExchange -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(chainCalled).isFalse();
    }

    private MockServerWebExchange exchange(String path, String token) {
        return MockServerWebExchange.from(MockServerHttpRequest.get(path)
                .header("Authorization", "Bearer " + token)
                .header("X-User-Email", "forged@aupp.edu")
                .header("X-User-Role", "TEACHER")
                .build());
    }

    private String token(String email, String role) {
        return Jwts.builder()
                .subject(email)
                .claim("role", role)
                .claim("fullName", email)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();
    }
}
