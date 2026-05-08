package com.aupp.registration.exception;

import com.aupp.registration.controller.RegistrationController;
import com.aupp.registration.dto.RegisterRequest;
import com.aupp.registration.dto.UserResponse;
import com.aupp.registration.service.RegistrationService;
import com.aupp.registration.web.ApiResponseAdvice;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RegistrationController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, ApiResponseAdvice.class})
@TestPropertySource(properties = "spring.data.mongodb.uri=")
class GlobalExceptionHandlerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @MockBean RegistrationService service;

    @Test
    void duplicateEmailMappedTo409() throws Exception {
        when(service.register(any())).thenThrow(new UserAlreadyExistsException("dup"));

        mvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new RegisterRequest("a@b.c", "secret123", "student"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("409"))
                .andExpect(jsonPath("$.message").value("dup"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void illegalArgumentMappedTo400() throws Exception {
        when(service.register(any())).thenThrow(new IllegalArgumentException("bad role"));

        mvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new RegisterRequest("a@b.c", "secret123", "student"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"))
                .andExpect(jsonPath("$.message").value("bad role"));
    }

    @Test
    void validationErrorIncludesFieldSummary() throws Exception {
        mvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\",\"password\":\"\",\"role\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"))
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.startsWith("Validation failed")));
    }

    @Test
    void uncaughtExceptionMappedTo500() throws Exception {
        when(service.register(any())).thenThrow(new RuntimeException("boom"));

        mvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new RegisterRequest("a@b.c", "secret123", "student"))))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("500"))
                .andExpect(jsonPath("$.message").value("Internal server error"));
    }

    @Test
    void successPathIsWrappedInEnvelope() throws Exception {
        UserResponse u = new UserResponse("id-1", "a@b.c", "student", Instant.now());
        when(service.register(any())).thenReturn(u);

        mvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new RegisterRequest("a@b.c", "secret123", "student"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("201"))
                .andExpect(jsonPath("$.message").value("Created"))
                .andExpect(jsonPath("$.data.id").value("id-1"))
                .andExpect(jsonPath("$.pagination").doesNotExist());
    }
}
