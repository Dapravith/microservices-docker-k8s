package com.aupp.login.service;

import com.aupp.login.dto.LoginRequest;
import com.aupp.login.dto.TokenResponse;

public interface AuthService {

    TokenResponse login(LoginRequest req);
}
