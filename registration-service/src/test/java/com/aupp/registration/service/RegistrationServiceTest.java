package com.aupp.registration.service;

import com.aupp.registration.domain.Role;
import com.aupp.registration.domain.User;
import com.aupp.registration.dto.RegisterRequest;
import com.aupp.registration.dto.UserResponse;
import com.aupp.registration.exception.UserAlreadyExistsException;
import com.aupp.registration.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    @Mock UserRepository users;
    @Mock PasswordEncoder encoder;
    @InjectMocks RegistrationService service;

    @Test
    void registerHashesPasswordAndPersistsUser() {
        when(users.existsByEmail("alice@x.y")).thenReturn(false);
        when(encoder.encode("password1")).thenReturn("hashed");
        when(users.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId("id-1");
            return u;
        });

        UserResponse resp = service.register(new RegisterRequest("alice@x.y", "password1", "student"));

        assertThat(resp.id()).isEqualTo("id-1");
        assertThat(resp.email()).isEqualTo("alice@x.y");
        assertThat(resp.role()).isEqualTo("student");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(users).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("alice@x.y");
        assertThat(saved.getPasswordHash()).isEqualTo("hashed");
        assertThat(saved.getRole()).isEqualTo(Role.STUDENT);
    }

    @Test
    void registerLowercasesAndTrimsEmail() {
        when(users.existsByEmail("alice@x.y")).thenReturn(false);
        when(encoder.encode(anyString())).thenReturn("h");
        when(users.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse resp = service.register(new RegisterRequest("  AlIcE@X.Y  ", "password1", "student"));

        assertThat(resp.email()).isEqualTo("alice@x.y");
        verify(users).existsByEmail("alice@x.y");
    }

    @Test
    void registerExistingEmailRaises409() {
        when(users.existsByEmail(anyString())).thenReturn(true);

        assertThatThrownBy(() -> service.register(new RegisterRequest("dup@x.y", "password1", "student")))
                .isInstanceOf(UserAlreadyExistsException.class);
        verify(users, never()).save(any());
    }

    @Test
    void registerInvalidRoleRaises400() {
        assertThatThrownBy(() -> service.register(new RegisterRequest("a@b.c", "password1", "wizard")))
                .isInstanceOf(IllegalArgumentException.class);
        verify(users, never()).save(any());
    }

    @Test
    void registerHandlesRaceConditionDuplicateOnSave() {
        when(users.existsByEmail(anyString())).thenReturn(false);
        when(encoder.encode(anyString())).thenReturn("h");
        when(users.save(any(User.class))).thenThrow(new DuplicateKeyException("dup"));

        assertThatThrownBy(() -> service.register(new RegisterRequest("a@b.c", "password1", "student")))
                .isInstanceOf(UserAlreadyExistsException.class);
    }

    @Test
    void registerWithRoleStudentBypassesDtoRoleField() {
        when(users.existsByEmail(anyString())).thenReturn(false);
        when(encoder.encode(anyString())).thenReturn("h");
        when(users.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse resp = service.registerWithRole("a@b.c", "password1", Role.TEACHER);

        assertThat(resp.role()).isEqualTo("teacher");
    }
}
