package com.aupp.login.service;

import com.aupp.login.config.JwtProperties;
import com.aupp.login.domain.Role;
import com.aupp.login.domain.User;
import com.aupp.login.dto.LoginRequest;
import com.aupp.login.dto.RefreshRequest;
import com.aupp.login.dto.TokenResponse;
import com.aupp.login.exception.InvalidCredentialsException;
import com.aupp.login.repository.UserRepository;
import com.aupp.login.service.impl.AuthServiceImpl;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
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
import static org.mockito.Mockito.mock;
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
        jwtProps = new JwtProperties("test-secret-please-change-me-1234567890-abcdef", "issuer", 900, 604_800);
        service = new AuthServiceImpl(users, encoder, jwt, jwtProps);
    }

    @Test
    void loginWithValidCredentialsReturnsAccessAndRefreshTokens() {
        User u = User.builder().id("1").email("a@b.c").passwordHash("hash").role(Role.STUDENT).build();
        when(users.findByEmail("a@b.c")).thenReturn(Optional.of(u));
        when(encoder.matches("pwd", "hash")).thenReturn(true);
        when(jwt.issueAccess("a@b.c", Role.STUDENT)).thenReturn("signed.access.jwt");
        when(jwt.issueRefresh("a@b.c", Role.STUDENT)).thenReturn("signed.refresh.jwt");

        TokenResponse resp = service.login(new LoginRequest("a@b.c", "pwd", "student"));

        assertThat(resp.accessToken()).isEqualTo("signed.access.jwt");
        assertThat(resp.refreshToken()).isEqualTo("signed.refresh.jwt");
        assertThat(resp.tokenType()).isEqualTo("Bearer");
        assertThat(resp.accessExpiresInSeconds()).isEqualTo(900);
        assertThat(resp.refreshExpiresInSeconds()).isEqualTo(604_800);
        assertThat(resp.role()).isEqualTo("student");
    }

    @Test
    void loginLowercasesEmailBeforeLookup() {
        User u = User.builder().email("alice@x.y").passwordHash("h").role(Role.STUDENT).build();
        when(users.findByEmail("alice@x.y")).thenReturn(Optional.of(u));
        when(encoder.matches(anyString(), anyString())).thenReturn(true);
        when(jwt.issueAccess(anyString(), any())).thenReturn("a");
        when(jwt.issueRefresh(anyString(), any())).thenReturn("r");

        service.login(new LoginRequest("AlIcE@X.y", "pwd", "student"));

        verify(users).findByEmail("alice@x.y");
    }

    @Test
    void loginWithUnknownEmailRaises401() {
        when(users.findByEmail(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest("nope@x.y", "pwd", "student")))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(jwt, never()).issueAccess(anyString(), any());
        verify(jwt, never()).issueRefresh(anyString(), any());
    }

    @Test
    void loginWithBadPasswordRaises401() {
        User u = User.builder().email("a@b.c").passwordHash("h").role(Role.STUDENT).build();
        when(users.findByEmail(anyString())).thenReturn(Optional.of(u));
        when(encoder.matches("WRONG", "h")).thenReturn(false);

        assertThatThrownBy(() -> service.login(new LoginRequest("a@b.c", "WRONG", "student")))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(jwt, never()).issueAccess(anyString(), any());
    }

    @Test
    void loginWithMismatchedRoleRaises401() {
        User u = User.builder().email("a@b.c").passwordHash("h").role(Role.STUDENT).build();
        when(users.findByEmail(anyString())).thenReturn(Optional.of(u));
        when(encoder.matches(anyString(), anyString())).thenReturn(true);

        assertThatThrownBy(() -> service.login(new LoginRequest("a@b.c", "pwd", "teacher")))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(jwt, never()).issueAccess(anyString(), any());
    }

    @Test
    void loginWithInvalidRoleStringRaises400() {
        assertThatThrownBy(() -> service.login(new LoginRequest("a@b.c", "pwd", "admin")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refreshIssuesNewAccessTokenAndKeepsRefresh() {
        Claims claims = mock(Claims.class);
        when(claims.get("email", String.class)).thenReturn("alice@x.y");
        when(claims.get("role", String.class)).thenReturn("student");
        when(jwt.parseRefresh("good.refresh")).thenReturn(claims);
        when(jwt.issueAccess("alice@x.y", Role.STUDENT)).thenReturn("new.access");

        TokenResponse resp = service.refresh(new RefreshRequest("good.refresh"));

        assertThat(resp.accessToken()).isEqualTo("new.access");
        assertThat(resp.refreshToken()).isEqualTo("good.refresh");
        assertThat(resp.role()).isEqualTo("student");
        verify(jwt, never()).issueRefresh(anyString(), any());
    }

    @Test
    void refreshFallsBackToSubjectIfEmailClaimMissing() {
        Claims claims = mock(Claims.class);
        when(claims.get("email", String.class)).thenReturn(null);
        when(claims.getSubject()).thenReturn("bob@x.y");
        when(claims.get("role", String.class)).thenReturn("teacher");
        when(jwt.parseRefresh("good.refresh")).thenReturn(claims);
        when(jwt.issueAccess("bob@x.y", Role.TEACHER)).thenReturn("new.access");

        TokenResponse resp = service.refresh(new RefreshRequest("good.refresh"));

        assertThat(resp.accessToken()).isEqualTo("new.access");
    }

    @Test
    void refreshRejectsInvalidToken() {
        when(jwt.parseRefresh("bad.token")).thenThrow(new JwtException("bad sig"));

        assertThatThrownBy(() -> service.refresh(new RefreshRequest("bad.token")))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(jwt, never()).issueAccess(anyString(), any());
    }

    @Test
    void refreshRejectsTokenMissingRequiredClaims() {
        Claims claims = mock(Claims.class);
        when(claims.get("email", String.class)).thenReturn(null);
        when(claims.getSubject()).thenReturn(null);
        when(jwt.parseRefresh("partial.token")).thenReturn(claims);

        assertThatThrownBy(() -> service.refresh(new RefreshRequest("partial.token")))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(jwt, never()).issueAccess(anyString(), any());
    }
}
