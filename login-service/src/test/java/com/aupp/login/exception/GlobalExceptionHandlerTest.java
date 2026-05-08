package com.aupp.login.exception;

import com.aupp.login.controller.AuthController;
import com.aupp.login.dto.LoginRequest;
import com.aupp.login.dto.TokenResponse;
import com.aupp.login.service.AuthService;
import com.aupp.login.web.ApiResponseAdvice;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, ApiResponseAdvice.class})
class GlobalExceptionHandlerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @MockBean AuthService auth;

    @Test
    void invalidCredentialsMappedTo401WithApiResponseEnvelope() throws Exception {
        when(auth.login(any())).thenThrow(new InvalidCredentialsException("bad"));

        mvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new LoginRequest("a@b.c", "x", "student"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("401"))
                .andExpect(jsonPath("$.message").value("bad"))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.pagination").doesNotExist());
    }

    @Test
    void illegalArgumentMappedTo400() throws Exception {
        when(auth.login(any())).thenThrow(new IllegalArgumentException("bad role"));

        mvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new LoginRequest("a@b.c", "pwd", "student"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"))
                .andExpect(jsonPath("$.message").value("bad role"));
    }

    @Test
    void validationErrorIncludesFieldSummary() throws Exception {
        mvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\",\"password\":\"\",\"role\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"))
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.startsWith("Validation failed")));
    }

    @Test
    void uncaughtExceptionMappedTo500() throws Exception {
        when(auth.login(any())).thenThrow(new RuntimeException("boom"));

        mvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new LoginRequest("a@b.c", "pwd", "student"))))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("500"))
                .andExpect(jsonPath("$.message").value("Internal server error"));
    }

    @Test
    void successPathIsWrappedInEnvelope() throws Exception {
        when(auth.login(any())).thenReturn(TokenResponse.bearer("access.t", "refresh.t", 60, 600, "student"));
        mvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new LoginRequest("a@b.c", "pwd", "student"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.message").value("OK"))
                .andExpect(jsonPath("$.data.accessToken").value("access.t"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh.t"))
                .andExpect(jsonPath("$.pagination").doesNotExist());
    }
}
