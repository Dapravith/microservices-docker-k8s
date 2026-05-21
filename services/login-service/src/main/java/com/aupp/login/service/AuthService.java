package com.aupp.login.service;

import com.aupp.login.dto.AuthResponse;
import com.aupp.login.dto.LoginRequest;
import com.aupp.login.dto.RegisterRequest;
import com.aupp.login.dto.UserResponse;

public interface AuthService {
    UserResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);
}
