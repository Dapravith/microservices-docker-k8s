package com.aupp.login.service.impl;

import com.aupp.login.config.JwtProperties;
import com.aupp.login.domain.Role;
import com.aupp.login.service.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtServiceImpl implements JwtService {

    static final String CLAIM_TYP = "typ";
    static final String TYP_ACCESS = "access";
    static final String TYP_REFRESH = "refresh";

    private final JwtProperties props;
    private final SecretKey key;

    public JwtServiceImpl(JwtProperties props) {
        this.props = props;
        byte[] keyBytes = props.secret().getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
            keyBytes = padded;
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    @Override
    public String issueAccess(String email, Role role) {
        return build(email, role, TYP_ACCESS, props.accessExpirationSeconds());
    }

    @Override
    public String issueRefresh(String email, Role role) {
        return build(email, role, TYP_REFRESH, props.refreshExpirationSeconds());
    }

    @Override
    public Claims parseRefresh(String token) throws JwtException {
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        String typ = claims.get(CLAIM_TYP, String.class);
        if (!TYP_REFRESH.equals(typ)) {
            throw new JwtException("token typ is '" + typ + "', expected 'refresh'");
        }
        return claims;
    }

    private String build(String email, Role role, String typ, long ttlSeconds) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(ttlSeconds);
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(props.issuer())
                .subject(email)
                .claim("email", email)
                .claim("role", role.lower())
                .claim(CLAIM_TYP, typ)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key)
                .compact();
    }
}
