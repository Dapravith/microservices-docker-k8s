package com.aupp.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * Validates JWT on every protected route and enforces role-to-path mapping:
 *   /auth/**     -> public
 *   /student/**  -> requires role STUDENT
 *   /teacher/**  -> requires role TEACHER
 *
 * On success, propagates X-User-Email / X-User-Role headers to the downstream
 * service. Downstream services therefore do not need to know about JWTs.
 */
@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    private final SecretKey key;

    public JwtAuthFilter(@Value("${app.jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // Public routes: auth + actuator on the gateway itself
        if (path.startsWith("/auth/") || path.startsWith("/actuator/")) {
            return chain.filter(exchange);
        }

        String auth = request.getHeaders().getFirst("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return unauthorized(exchange, "missing Bearer token");
        }
        String token = auth.substring("Bearer ".length()).trim();

        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException e) {
            return unauthorized(exchange, "invalid or expired token");
        }

        String email = claims.getSubject();
        String role = claims.get("role", String.class);
        if (email == null || role == null) {
            return unauthorized(exchange, "token missing required claims");
        }

        String requiredRole = requiredRoleForPath(path);
        if (requiredRole != null && !requiredRole.equalsIgnoreCase(role)) {
            return forbidden(exchange,
                    "role '" + role + "' is not permitted to access " + path);
        }

        // Mutate request with identity headers (and strip any caller-supplied ones)
        ServerHttpRequest mutated = request.mutate()
                .headers(h -> {
                    h.remove("X-User-Email");
                    h.remove("X-User-Role");
                })
                .header("X-User-Email", email)
                .header("X-User-Role", role)
                .build();

        return chain.filter(exchange.mutate().request(mutated).build());
    }

    private String requiredRoleForPath(String path) {
        if (path.startsWith("/student")) return "STUDENT";
        if (path.startsWith("/teacher")) return "TEACHER";
        return null;
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String reason) {
        return writeJsonError(exchange, HttpStatus.UNAUTHORIZED, reason);
    }

    private Mono<Void> forbidden(ServerWebExchange exchange, String reason) {
        return writeJsonError(exchange, HttpStatus.FORBIDDEN, reason);
    }

    private Mono<Void> writeJsonError(ServerWebExchange exchange, HttpStatus status, String message) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"error\":\"" + status.getReasonPhrase() + "\",\"message\":\""
                + message.replace("\"", "\\\"") + "\"}";
        DataBuffer buffer = exchange.getResponse().bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    /** Run before the routing filter so we can short-circuit unauthenticated calls. */
    @Override
    public int getOrder() {
        return -1;
    }
}
