package com.aupp.registration.service;

import com.aupp.registration.domain.Role;
import com.aupp.registration.dto.RegisterRequest;
import com.aupp.registration.dto.UserResponse;

public interface RegistrationService {

    UserResponse register(RegisterRequest req);

    UserResponse registerWithRole(String email, String password, Role role);
}
