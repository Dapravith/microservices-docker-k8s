package com.aupp.login;

import com.aupp.login.dto.LoginRequest;
import com.aupp.login.dto.RegisterRequest;
import com.aupp.login.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@TestPropertySource(properties = {
        "app.seed.enabled=false",
        "de.flapdoodle.mongodb.embedded.version=7.0.5"
})
class AuthFlowIntegrationTest {

    @Autowired
    WebApplicationContext ctx;
    @Autowired
    UserRepository users;
    @Autowired
    ObjectMapper json;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        users.deleteAll();
        mvc = MockMvcBuilders.webAppContextSetup(ctx).build();
    }

    @Test
    void registerThenLoginReturnsToken() throws Exception {
        RegisterRequest reg = new RegisterRequest("alice@itc.edu.kh", "secret123", "student");
        mvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(reg)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("alice@itc.edu.kh"))
                .andExpect(jsonPath("$.role").value("student"));

        LoginRequest login = new LoginRequest("alice@itc.edu.kh", "secret123", "student");
        mvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.role").value("student"));
    }

    @Test
    void loginWithWrongRoleIsRejected() throws Exception {
        RegisterRequest reg = new RegisterRequest("bob@itc.edu.kh", "secret123", "teacher");
        mvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(reg)))
                .andExpect(status().isCreated());

        LoginRequest login = new LoginRequest("bob@itc.edu.kh", "secret123", "student");
        mvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(login)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginWithBadPasswordIsRejected() throws Exception {
        RegisterRequest reg = new RegisterRequest("carol@itc.edu.kh", "secret123", "student");
        mvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(reg)))
                .andExpect(status().isCreated());

        LoginRequest login = new LoginRequest("carol@itc.edu.kh", "WRONG-PWD", "student");
        mvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(login)))
                .andExpect(status().isUnauthorized());
    }
}
