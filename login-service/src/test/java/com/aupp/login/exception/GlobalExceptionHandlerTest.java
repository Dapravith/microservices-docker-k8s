package com.aupp.login.exception;

import com.aupp.login.controller.AuthController;
import com.aupp.login.dto.LoginRequest;
import com.aupp.login.dto.TokenResponse;
import com.aupp.login.service.AuthService;
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
@Import(GlobalExceptionHandler.class)
class GlobalExceptionHandlerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @MockBean AuthService auth;

    @Test
    void invalidCredentialsMappedTo401WithApiError() throws Exception {
        when(auth.login(any())).thenThrow(new InvalidCredentialsException("bad"));

        mvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new LoginRequest("a@b.c", "x", "student"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("bad"))
                .andExpect(jsonPath("$.path").value("/login"));
    }

    @Test
    void illegalArgumentMappedTo400() throws Exception {
        when(auth.login(any())).thenThrow(new IllegalArgumentException("bad role"));

        mvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new LoginRequest("a@b.c", "pwd", "student"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("bad role"));
    }

    @Test
    void validationErrorReturnsFieldDetails() throws Exception {
        mvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\",\"password\":\"\",\"role\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.details.fields").exists());
    }

    @Test
    void uncaughtExceptionMappedTo500() throws Exception {
        when(auth.login(any())).thenThrow(new RuntimeException("boom"));

        mvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new LoginRequest("a@b.c", "pwd", "student"))))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Internal server error"));
    }

    @Test
    void successPathStillWorks() throws Exception {
        when(auth.login(any())).thenReturn(TokenResponse.bearer("t", 60, "student"));
        mvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new LoginRequest("a@b.c", "pwd", "student"))))
                .andExpect(status().isOk());
    }
}
