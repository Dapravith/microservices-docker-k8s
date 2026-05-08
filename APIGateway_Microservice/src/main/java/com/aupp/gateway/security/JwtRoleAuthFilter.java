package com.aupp.gateway.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class JwtRoleAuthFilter extends AbstractGatewayFilterFactory<JwtRoleAuthFilter.Config> {

    private static final Logger log = LoggerFactory.getLogger(JwtRoleAuthFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JwtVerifier verifier;

    public JwtRoleAuthFilter(JwtVerifier verifier) {
        super(Config.class);
        this.verifier = verifier;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (header == null || !header.startsWith(BEARER_PREFIX)) {
                return reject(exchange, HttpStatus.UNAUTHORIZED, "Missing Bearer token in Authorization header");
            }
            String token = header.substring(BEARER_PREFIX.length()).trim();

            Claims claims;
            try {
                claims = verifier.verify(token);
            } catch (JwtException ex) {
                log.debug("token verification failed: {}", ex.getMessage());
                return reject(exchange, HttpStatus.UNAUTHORIZED, "Invalid or expired token");
            }

            String typ = claims.get("typ", String.class);
            if (!"access".equals(typ)) {
                return reject(exchange, HttpStatus.UNAUTHORIZED,
                        "Use an access token here (typ=access). Got typ=" + typ + ". Refresh tokens are only valid at /refresh.");
            }

            String role = claims.get("role", String.class);
            String emailClaim = claims.get("email", String.class);
            final String email = emailClaim != null ? emailClaim : claims.getSubject();
            if (role == null || !role.equalsIgnoreCase(config.getRequiredRole())) {
                return reject(exchange, HttpStatus.FORBIDDEN,
                        "Forbidden: route requires role=" + config.getRequiredRole() + ", you have role=" + role);
            }
            final String roleLower = role.toLowerCase();

            ServerWebExchange mutated = exchange.mutate().request(req ->
                    req.headers(h -> {
                        h.set("X-User-Email", email == null ? "" : email);
                        h.set("X-User-Role", roleLower);
                    })
            ).build();
            return chain.filter(mutated);
        };
    }

    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", String.valueOf(status.value()));
        body.put("message", message);
        byte[] bytes;
        try {
            bytes = MAPPER.writeValueAsBytes(body);
        } catch (JsonProcessingException e) {
            bytes = ("{\"code\":\"" + status.value() + "\",\"message\":\"" + message + "\"}").getBytes();
        }
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    public static class Config {
        private String requiredRole;

        public String getRequiredRole() {
            return requiredRole;
        }

        public void setRequiredRole(String requiredRole) {
            this.requiredRole = requiredRole;
        }
    }
}
