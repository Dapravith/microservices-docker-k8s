package com.aupp.login;

import com.aupp.login.domain.Role;
import com.aupp.login.domain.User;
import com.aupp.login.dto.LoginRequest;
import com.aupp.login.dto.RefreshRequest;
import com.aupp.login.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@TestPropertySource(properties = {
        "de.flapdoodle.mongodb.embedded.version=7.0.5",
        "spring.data.mongodb.uri=mongodb://localhost/test"
})
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
    void loginReturnsAccessAndRefreshTokensForSeededUser() throws Exception {
        seedUser("alice@itc.edu.kh", "secret123", Role.STUDENT);

        mvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new LoginRequest("alice@itc.edu.kh", "secret123", "student"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.role").value("student"))
                .andExpect(jsonPath("$.data.accessExpiresInSeconds").isNumber())
                .andExpect(jsonPath("$.data.refreshExpiresInSeconds").isNumber());
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

    @Test
    void refreshExchangesRefreshTokenForNewAccessToken() throws Exception {
        seedUser("dan@itc.edu.kh", "secret123", Role.STUDENT);

        MvcResult login = mvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new LoginRequest("dan@itc.edu.kh", "secret123", "student"))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode loginBody = json.readTree(login.getResponse().getContentAsString()).get("data");
        String refreshToken = loginBody.get("refreshToken").asText();
        String originalAccess = loginBody.get("accessToken").asText();

        MvcResult refresh = mvc.perform(post("/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new RefreshRequest(refreshToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").value(refreshToken))
                .andExpect(jsonPath("$.data.role").value("student"))
                .andReturn();

        JsonNode refreshBody = json.readTree(refresh.getResponse().getContentAsString()).get("data");
        assertThat(refreshBody.get("accessToken").asText()).isNotEqualTo(originalAccess);
    }

    @Test
    void refreshRejectsAccessTokenAsRefresh() throws Exception {
        seedUser("erin@itc.edu.kh", "secret123", Role.TEACHER);

        MvcResult login = mvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new LoginRequest("erin@itc.edu.kh", "secret123", "teacher"))))
                .andReturn();
        String accessToken = json.readTree(login.getResponse().getContentAsString()).get("data").get("accessToken").asText();

        mvc.perform(post("/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new RefreshRequest(accessToken))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshRejectsGarbageToken() throws Exception {
        mvc.perform(post("/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new RefreshRequest("not-a-real-jwt"))))
                .andExpect(status().isUnauthorized());
    }
}
