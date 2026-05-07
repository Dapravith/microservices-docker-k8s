package com.aupp.login;

import com.aupp.login.domain.Role;
import com.aupp.login.domain.User;
import com.aupp.login.dto.LoginRequest;
import com.aupp.login.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@TestPropertySource(properties = "de.flapdoodle.mongodb.embedded.version=7.0.5")
class AuthFlowIntegrationTest {

    @Autowired WebApplicationContext ctx;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper json;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        users.deleteAll();
        mvc = MockMvcBuilders.webAppContextSetup(ctx).build();
    }

    private User seedUser(String email, String password, Role role) {
        return users.save(User.builder()
                .email(email)
                .passwordHash(encoder.encode(password))
                .role(role)
                .build());
    }

    @Test
    void loginReturnsTokenForSeededUser() throws Exception {
        seedUser("alice@itc.edu.kh", "secret123", Role.STUDENT);

        mvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new LoginRequest("alice@itc.edu.kh", "secret123", "student"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.role").value("student"));
    }

    @Test
    void loginWithWrongRoleIsRejected() throws Exception {
        seedUser("bob@itc.edu.kh", "secret123", Role.TEACHER);

        mvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new LoginRequest("bob@itc.edu.kh", "secret123", "student"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginWithBadPasswordIsRejected() throws Exception {
        seedUser("carol@itc.edu.kh", "secret123", Role.STUDENT);

        mvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new LoginRequest("carol@itc.edu.kh", "WRONG-PWD", "student"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginForUnknownEmailReturns401() throws Exception {
        mvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new LoginRequest("ghost@itc.edu.kh", "x", "student"))))
                .andExpect(status().isUnauthorized());
    }
}
