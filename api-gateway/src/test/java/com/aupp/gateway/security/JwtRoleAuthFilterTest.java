package com.aupp.gateway.security;

import com.aupp.gateway.config.JwtProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class JwtRoleAuthFilterTest {

    private static final String SECRET = "test-secret-please-change-me-1234567890-abcdef";

    private JwtRoleAuthFilter filterFactory;
    private SecretKey key;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties(SECRET);
        filterFactory = new JwtRoleAuthFilter(new JwtVerifier(props));
        key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }

    private String token(String email, String role, long ttlSeconds) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + ttlSeconds * 1000);
        return Jwts.builder()
                .subject(email)
                .claim("email", email)
                .claim("role", role)
                .issuedAt(now)
                .expiration(exp)
                .signWith(key)
                .compact();
    }

    @Test
    void missingBearerReturns401() {
        GatewayFilter f = filterFactory.apply(role("student"));
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/student/x"));
        f.filter(exchange, passthrough()).block();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void wrongRoleReturns403() {
        GatewayFilter f = filterFactory.apply(role("student"));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/student/x")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("a@b.c", "teacher", 3600)));
        f.filter(exchange, passthrough()).block();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void rightRolePassesThroughAndStampsHeaders() {
        GatewayFilter f = filterFactory.apply(role("student"));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/student/x")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("a@b.c", "student", 3600)));

        boolean[] reachedDownstream = {false};
        GatewayFilterChain chain = ex -> {
            reachedDownstream[0] = true;
            assertThat(ex.getRequest().getHeaders().getFirst("X-User-Email")).isEqualTo("a@b.c");
            assertThat(ex.getRequest().getHeaders().getFirst("X-User-Role")).isEqualTo("student");
            return Mono.empty();
        };
        f.filter(exchange, chain).block();
        assertThat(reachedDownstream[0]).isTrue();
    }

    @Test
    void expiredTokenReturns401() {
        GatewayFilter f = filterFactory.apply(role("student"));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/student/x")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("a@b.c", "student", -10)));
        f.filter(exchange, passthrough()).block();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private JwtRoleAuthFilter.Config role(String r) {
        JwtRoleAuthFilter.Config cfg = new JwtRoleAuthFilter.Config();
        cfg.setRequiredRole(r);
        return cfg;
    }

    private GatewayFilterChain passthrough() {
        return (ServerWebExchange ex) -> Mono.empty();
    }
}
