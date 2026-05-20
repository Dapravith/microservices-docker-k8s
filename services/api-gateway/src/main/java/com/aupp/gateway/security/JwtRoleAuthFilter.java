package com.aupp.gateway.security;

import java.nio.charset.StandardCharsets;

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

@Component
public class JwtRoleAuthFilter implements GlobalFilter, Ordered {

    private final JwtVerifier jwtVerifier;

    public JwtRoleAuthFilter(JwtVerifier jwtVerifier) {
        this.jwtVerifier = jwtVerifier;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        String authorization = request.getHeaders().getFirst("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return writeJsonError(exchange, HttpStatus.UNAUTHORIZED, "missing Bearer token");
        }

        AuthenticatedUser user;
        try {
            user = jwtVerifier.verify(authorization.substring("Bearer ".length()).trim());
        } catch (InvalidJwtException ex) {
            return writeJsonError(exchange, HttpStatus.UNAUTHORIZED, ex.getMessage());
        }

        String requiredRole = requiredRole(path);
        if (requiredRole != null && !requiredRole.equalsIgnoreCase(user.role())) {
            return writeJsonError(
                    exchange,
                    HttpStatus.FORBIDDEN,
                    "role '" + user.role() + "' is not permitted to access " + path
            );
        }

        ServerHttpRequest mutated = request.mutate()
                .headers(headers -> {
                    headers.remove("X-User-Email");
                    headers.remove("X-User-Role");
                    headers.remove("X-User-Full-Name");
                })
                .header("X-User-Email", user.email())
                .header("X-User-Role", user.role())
                .header("X-User-Full-Name", user.fullName())
                .build();

        return chain.filter(exchange.mutate().request(mutated).build());
    }

    boolean isPublicPath(String path) {
        return path.startsWith("/auth/") || path.equals("/auth")
                || path.startsWith("/actuator/") || path.equals("/actuator");
    }

    String requiredRole(String path) {
        if (path.startsWith("/student")) {
            return "STUDENT";
        }
        if (path.startsWith("/teacher")) {
            return "TEACHER";
        }
        return null;
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

    @Override
    public int getOrder() {
        return -1;
    }
}
