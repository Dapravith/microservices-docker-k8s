package com.aupp.teacher.security;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Defense-in-depth: re-verifies the JWT inside the service rather than trusting
 * the gateway-injected X-User-* headers. Rejects wrong-role tokens with 403 so a
 * STUDENT token can never reach teacher endpoints even if the gateway is bypassed.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String REQUIRED_ROLE = "TEACHER";

    private final JwtVerifier jwtVerifier;

    public JwtAuthFilter(JwtVerifier jwtVerifier) {
        this.jwtVerifier = jwtVerifier;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // /actuator is for probes; /internal is the unauthenticated service-to-service endpoint.
        return path.startsWith("/actuator") || path.startsWith("/internal");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized", "missing Bearer token");
            return;
        }

        AuthenticatedUser user;
        try {
            user = jwtVerifier.verify(authorization.substring("Bearer ".length()).trim());
        } catch (InvalidJwtException ex) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized", ex.getMessage());
            return;
        }

        if (!REQUIRED_ROLE.equalsIgnoreCase(user.role())) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN, "Forbidden",
                    "role '" + user.role() + "' is not permitted to access teacher-service");
            return;
        }

        chain.doFilter(new IdentityRequest(request, user), response);
    }

    private void writeError(HttpServletResponse response, int status, String error, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"status\":" + status + ",\"error\":\"" + error + "\",\"message\":\""
                        + message.replace("\"", "\\\"") + "\"}");
    }

    /**
     * Exposes the verified identity through the X-User-* headers so controllers
     * read trusted token claims instead of whatever the caller supplied.
     */
    private static final class IdentityRequest extends HttpServletRequestWrapper {

        private final Map<String, String> identity;

        IdentityRequest(HttpServletRequest request, AuthenticatedUser user) {
            super(request);
            this.identity = Map.of(
                    "x-user-email", user.email(),
                    "x-user-role", user.role(),
                    "x-user-full-name", user.fullName());
        }

        @Override
        public String getHeader(String name) {
            String override = identity.get(name.toLowerCase());
            return override != null ? override : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            String override = identity.get(name.toLowerCase());
            return override != null
                    ? Collections.enumeration(List.of(override))
                    : super.getHeaders(name);
        }
    }
}
