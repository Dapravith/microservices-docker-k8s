package com.aupp.login.service;

import com.aupp.login.config.JwtProperties;
import com.aupp.login.domain.Role;
import com.aupp.login.domain.User;
import com.aupp.login.dto.LoginRequest;
import com.aupp.login.dto.RegisterRequest;
import com.aupp.login.dto.TokenResponse;
import com.aupp.login.dto.UserResponse;
import com.aupp.login.exception.InvalidCredentialsException;
import com.aupp.login.exception.UserAlreadyExistsException;
import com.aupp.login.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
        service = new AuthService(users, encoder, jwt, jwtProps);
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

    @Test
    void registerCreatesNewUserAndHashesPassword() {
        when(users.existsByEmail("alice@x.y")).thenReturn(false);
        when(encoder.encode("pwd")).thenReturn("hashed");
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        when(users.save(captor.capture())).thenAnswer(inv -> {
            User saved = inv.getArgument(0);
            saved.setId("generated-id");
            return saved;
        });

        UserResponse resp = service.register(new RegisterRequest("alice@x.y", "pwd", "student"));

        assertThat(resp.id()).isEqualTo("generated-id");
        assertThat(resp.email()).isEqualTo("alice@x.y");
        assertThat(resp.role()).isEqualTo("student");

        User saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("alice@x.y");
        assertThat(saved.getPasswordHash()).isEqualTo("hashed");
        assertThat(saved.getRole()).isEqualTo(Role.STUDENT);
    }

    @Test
    void registerLowercasesEmail() {
        when(users.existsByEmail("alice@x.y")).thenReturn(false);
        when(encoder.encode(anyString())).thenReturn("h");
        when(users.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse resp = service.register(new RegisterRequest("AlIcE@X.Y", "pwd", "teacher"));

        assertThat(resp.email()).isEqualTo("alice@x.y");
        verify(users).existsByEmail("alice@x.y");
    }

    @Test
    void registerExistingEmailRaises409() {
        when(users.existsByEmail(anyString())).thenReturn(true);

        assertThatThrownBy(() -> service.register(new RegisterRequest("dup@x.y", "pwd", "student")))
                .isInstanceOf(UserAlreadyExistsException.class);
        verify(users, never()).save(any());
    }

    @Test
    void registerWithInvalidRoleRaises400() {
        assertThatThrownBy(() -> service.register(new RegisterRequest("a@b.c", "pwd", "wizard")))
                .isInstanceOf(IllegalArgumentException.class);
        verify(users, never()).save(any());
    }
}
