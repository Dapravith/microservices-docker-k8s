package com.aupp.login.service;

import com.aupp.login.domain.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;

public interface JwtService {

    /** Issue a short-lived access token (typ=access). */
    String issueAccess(String email, Role role);

    /** Issue a long-lived refresh token (typ=refresh). */
    String issueRefresh(String email, Role role);

    /** Verify signature/expiry and require typ=refresh. Throws JwtException otherwise. */
    Claims parseRefresh(String token) throws JwtException;
}
