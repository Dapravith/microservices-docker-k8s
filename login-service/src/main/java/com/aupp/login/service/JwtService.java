package com.aupp.login.service;

import com.aupp.login.domain.Role;

public interface JwtService {

    String issue(String email, Role role);
}
