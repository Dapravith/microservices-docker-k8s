package com.aupp.registration;

import com.aupp.registration.dto.RegisterRequest;
import com.aupp.registration.dto.RoleScopedRegisterRequest;
import com.aupp.registration.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "de.flapdoodle.mongodb.embedded.version=7.0.5",
        "spring.data.mongodb.uri=mongodb://localhost/test"
})
class RegistrationFlowIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired ObjectMapper json;

    @BeforeEach
    void clean() {
        users.deleteAll();
    }

    @Test
    void registerCreatesUserWithLowercaseEmail() throws Exception {
        RegisterRequest req = new RegisterRequest("AlIcE@itc.edu.kh", "secret123", "student");

        mvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("201"))
                .andExpect(jsonPath("$.message").value("Created"))
                .andExpect(jsonPath("$.data.email").value("alice@itc.edu.kh"))
                .andExpect(jsonPath("$.data.role").value("student"))
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.pagination").doesNotExist());
    }

    @Test
    void duplicateEmailReturns409() throws Exception {
        RegisterRequest first = new RegisterRequest("dup@itc.edu.kh", "secret123", "student");
        mvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(first)))
                .andExpect(status().isCreated());

        mvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(first)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("409"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("already exists")));
    }

    @Test
    void invalidEmailIsRejected() throws Exception {
        mvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"secret123\",\"role\":\"student\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"))
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("email")));
    }

    @Test
    void shortPasswordIsRejected() throws Exception {
        mvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.c\",\"password\":\"short1\",\"role\":\"student\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("password")));
    }

    @Test
    void passwordWithoutDigitIsRejected() throws Exception {
        mvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.c\",\"password\":\"NoDigitsHere\",\"role\":\"student\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("password")));
    }

    @Test
    void invalidRoleIsRejected() throws Exception {
        mvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.c\",\"password\":\"secret123\",\"role\":\"admin\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("role")));
    }

    @Test
    void registerStudentEndpointSetsRoleAutomatically() throws Exception {
        RoleScopedRegisterRequest req = new RoleScopedRegisterRequest("bob@itc.edu.kh", "secret123");

        mvc.perform(post("/register/student")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.role").value("student"));
    }

    @Test
    void registerTeacherEndpointSetsRoleAutomatically() throws Exception {
        RoleScopedRegisterRequest req = new RoleScopedRegisterRequest("ms.smith@itc.edu.kh", "secret123");

        mvc.perform(post("/register/teacher")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.role").value("teacher"));
    }
}
