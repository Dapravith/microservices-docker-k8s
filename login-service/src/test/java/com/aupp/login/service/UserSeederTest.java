package com.aupp.login.service;

import com.aupp.login.domain.Role;
import com.aupp.login.domain.User;
import com.aupp.login.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserSeederTest {

    @Mock UserRepository users;
    @Mock PasswordEncoder encoder;

    @Test
    void seedDoesNothingWhenDisabled() {
        UserSeeder seeder = new UserSeeder(users, encoder, false);

        seeder.seed();

        verify(users, never()).save(any());
        verify(users, never()).existsByEmail(anyString());
    }

    @Test
    void seedCreatesBothDemoUsersWhenAbsent() {
        UserSeeder seeder = new UserSeeder(users, encoder, true);
        when(users.existsByEmail(anyString())).thenReturn(false);
        when(encoder.encode(anyString())).thenReturn("hashed");
        when(users.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        seeder.seed();

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(users, times(2)).save(captor.capture());
        List<User> saved = captor.getAllValues();
        assertThat(saved)
                .extracting(User::getEmail, User::getRole)
                .containsExactly(
                        org.assertj.core.api.Assertions.tuple("student1@itc.edu.kh", Role.STUDENT),
                        org.assertj.core.api.Assertions.tuple("teacher1@itc.edu.kh", Role.TEACHER));
    }

    @Test
    void seedSkipsAlreadyPresentUsers() {
        UserSeeder seeder = new UserSeeder(users, encoder, true);
        when(users.existsByEmail("student1@itc.edu.kh")).thenReturn(true);
        when(users.existsByEmail("teacher1@itc.edu.kh")).thenReturn(false);
        when(encoder.encode(anyString())).thenReturn("h");
        when(users.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        seeder.seed();

        verify(users, times(1)).save(any(User.class));
    }
}
