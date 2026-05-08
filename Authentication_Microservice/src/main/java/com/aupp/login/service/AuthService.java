package com.aupp.login.service;

import com.aupp.login.dto.LoginRequest;
import com.aupp.login.dto.RefreshRequest;
import com.aupp.login.dto.TokenResponse;

public interface AuthService {

    TokenResponse login(LoginRequest req);

    /** Validate a refresh token and mint a new access token (refresh token is reused, not rotated). */
    TokenResponse refresh(RefreshRequest req);
}
