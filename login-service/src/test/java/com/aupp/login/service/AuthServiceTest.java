package com.aupp.login.service;

import com.aupp.login.config.JwtProperties;
import com.aupp.login.domain.Role;
import com.aupp.login.domain.User;
import com.aupp.login.dto.LoginRequest;
import com.aupp.login.dto.TokenResponse;
import com.aupp.login.exception.InvalidCredentialsException;
import com.aupp.login.repository.UserRepository;
import com.aupp.login.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository users;
    @Mock PasswordEncoder encoder;
    @Mock JwtService jwt;

    JwtProperties jwtProps;
    AuthService service;

    @BeforeEach
    void setUp() {
        jwtProps = new JwtProperties("test-secret-please-change-me-1234567890-abcdef", "issuer", 3600);
        service = new AuthServiceImpl(users, encoder, jwt, jwtProps);
    }

    @Test
    void loginWithValidCredentialsReturnsToken() {
        User u = User.builder().id("1").email("a@b.c").passwordHash("hash").role(Role.STUDENT).build();
        when(users.findByEmail("a@b.c")).thenReturn(Optional.of(u));
        when(encoder.matches("pwd", "hash")).thenReturn(true);
        when(jwt.issue("a@b.c", Role.STUDENT)).thenReturn("signed.jwt.token");

        TokenResponse resp = service.login(new LoginRequest("a@b.c", "pwd", "student"));

        assertThat(resp.token()).isEqualTo("signed.jwt.token");
        assertThat(resp.tokenType()).isEqualTo("Bearer");
        assertThat(resp.expiresInSeconds()).isEqualTo(3600);
        assertThat(resp.role()).isEqualTo("student");
    }

    @Test
    void loginLowercasesEmailBeforeLookup() {
        User u = User.builder().email("alice@x.y").passwordHash("h").role(Role.STUDENT).build();
        when(users.findByEmail("alice@x.y")).thenReturn(Optional.of(u));
        when(encoder.matches(anyString(), anyString())).thenReturn(true);
        when(jwt.issue(anyString(), any())).thenReturn("t");

        service.login(new LoginRequest("AlIcE@X.y", "pwd", "student"));

        verify(users).findByEmail("alice@x.y");
    }

    @Test
    void loginWithUnknownEmailRaises401() {
        when(users.findByEmail(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest("nope@x.y", "pwd", "student")))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(jwt, never()).issue(anyString(), any());
    }

    @Test
    void loginWithBadPasswordRaises401() {
        User u = User.builder().email("a@b.c").passwordHash("h").role(Role.STUDENT).build();
        when(users.findByEmail(anyString())).thenReturn(Optional.of(u));
        when(encoder.matches("WRONG", "h")).thenReturn(false);

        assertThatThrownBy(() -> service.login(new LoginRequest("a@b.c", "WRONG", "student")))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(jwt, never()).issue(anyString(), any());
    }

    @Test
    void loginWithMismatchedRoleRaises401() {
        User u = User.builder().email("a@b.c").passwordHash("h").role(Role.STUDENT).build();
        when(users.findByEmail(anyString())).thenReturn(Optional.of(u));
        when(encoder.matches(anyString(), anyString())).thenReturn(true);

        assertThatThrownBy(() -> service.login(new LoginRequest("a@b.c", "pwd", "teacher")))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(jwt, never()).issue(anyString(), any());
    }

    @Test
    void loginWithInvalidRoleStringRaises400() {
        assertThatThrownBy(() -> service.login(new LoginRequest("a@b.c", "pwd", "admin")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
