package com.aupp.student.exception;

import com.aupp.student.controller.AssignmentController;
import com.aupp.student.dto.AssignmentResponse;
import com.aupp.student.dto.SubmitAssignmentRequest;
import com.aupp.student.service.AssignmentService;
import com.aupp.student.web.CallerIdentity;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AssignmentController.class)
@Import(GlobalExceptionHandler.class)
@TestPropertySource(properties = "spring.data.mongodb.uri=")
class GlobalExceptionHandlerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @MockBean AssignmentService service;

    @Test
    void missingCallerIdentityMappedTo400() throws Exception {
        when(service.listMine(any())).thenThrow(new MissingCallerIdentityException("identity missing"));

        mvc.perform(get("/viewassignment"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("identity missing"))
                .andExpect(jsonPath("$.path").value("/viewassignment"));
    }

    @Test
    void assignmentNotFoundMappedTo404() throws Exception {
        when(service.updateLatest(any(), any())).thenThrow(new AssignmentNotFoundException("nope"));

        mvc.perform(post("/submitassignment")
                        .header(CallerIdentity.EMAIL_HEADER, "a@b.c")
                        .header(CallerIdentity.ROLE_HEADER, "student")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new SubmitAssignmentRequest("HW", "x"))))
                .andExpect(status().isCreated()); // sanity: post path uses submit, not update

        when(service.submit(any(), any())).thenThrow(new AssignmentNotFoundException("not there"));
        mvc.perform(post("/submitassignment")
                        .header(CallerIdentity.EMAIL_HEADER, "a@b.c")
                        .header(CallerIdentity.ROLE_HEADER, "student")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new SubmitAssignmentRequest("HW", "x"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("not there"));
    }

    @Test
    void validationErrorReturnsFieldDetails() throws Exception {
        mvc.perform(post("/submitassignment")
                        .header(CallerIdentity.EMAIL_HEADER, "a@b.c")
                        .header(CallerIdentity.ROLE_HEADER, "student")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"content\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.details.fields.title").exists());
    }

    @Test
    void uncaughtExceptionMappedTo500() throws Exception {
        when(service.listMine(any())).thenThrow(new RuntimeException("boom"));

        mvc.perform(get("/viewassignment"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Internal server error"));
    }

    @Test
    void successPathReturns201() throws Exception {
        AssignmentResponse r = new AssignmentResponse("1", "a@b.c", "HW", "x", "SUBMITTED", Instant.now(), null);
        when(service.submit(any(), any())).thenReturn(r);

        mvc.perform(post("/submitassignment")
                        .header(CallerIdentity.EMAIL_HEADER, "a@b.c")
                        .header(CallerIdentity.ROLE_HEADER, "student")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new SubmitAssignmentRequest("HW", "x"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("1"));
    }
}
