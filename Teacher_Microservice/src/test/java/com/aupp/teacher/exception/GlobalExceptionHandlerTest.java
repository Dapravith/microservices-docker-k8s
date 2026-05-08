package com.aupp.teacher.exception;

import com.aupp.teacher.controller.TeacherAssignmentController;
import com.aupp.teacher.dto.AssignmentResponse;
import com.aupp.teacher.dto.CreateAssignmentRequest;
import com.aupp.teacher.service.TeacherAssignmentService;
import com.aupp.teacher.web.ApiResponseAdvice;
import com.aupp.teacher.web.CallerIdentity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TeacherAssignmentController.class)
@Import({GlobalExceptionHandler.class, ApiResponseAdvice.class})
@TestPropertySource(properties = "spring.data.mongodb.uri=")
class GlobalExceptionHandlerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @MockBean TeacherAssignmentService service;

    @Test
    void missingCallerIdentityMappedTo400() throws Exception {
        when(service.create(any(), any())).thenThrow(new MissingCallerIdentityException("identity missing"));

        mvc.perform(post("/addassignment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new CreateAssignmentRequest("X", null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"))
                .andExpect(jsonPath("$.message").value("identity missing"));
    }

    @Test
    void assignmentNotFoundMappedTo404() throws Exception {
        doThrow(new AssignmentNotFoundException("nope")).when(service).remove(any(), anyString());

        mvc.perform(delete("/removeassignment/abc")
                        .header(CallerIdentity.EMAIL_HEADER, "ms@x.y")
                        .header(CallerIdentity.ROLE_HEADER, "teacher"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("404"))
                .andExpect(jsonPath("$.message").value("nope"));
    }

    @Test
    void validationErrorIncludesFieldSummary() throws Exception {
        mvc.perform(post("/addassignment")
                        .header(CallerIdentity.EMAIL_HEADER, "ms@x.y")
                        .header(CallerIdentity.ROLE_HEADER, "teacher")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"description\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"))
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.startsWith("Validation failed")));
    }

    @Test
    void uncaughtExceptionMappedTo500() throws Exception {
        when(service.create(any(), any())).thenThrow(new RuntimeException("boom"));

        mvc.perform(post("/addassignment")
                        .header(CallerIdentity.EMAIL_HEADER, "ms@x.y")
                        .header(CallerIdentity.ROLE_HEADER, "teacher")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new CreateAssignmentRequest("X", null, null))))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("500"))
                .andExpect(jsonPath("$.message").value("Internal server error"));
    }

    @Test
    void successPathIsWrappedInEnvelope() throws Exception {
        AssignmentResponse r = new AssignmentResponse("1", "ms@x.y", "X", "", null, Instant.now(), null);
        when(service.create(any(), any())).thenReturn(r);

        mvc.perform(post("/addassignment")
                        .header(CallerIdentity.EMAIL_HEADER, "ms@x.y")
                        .header(CallerIdentity.ROLE_HEADER, "teacher")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new CreateAssignmentRequest("X", null, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("201"))
                .andExpect(jsonPath("$.message").value("Created"))
                .andExpect(jsonPath("$.data.id").value("1"));
    }
}
