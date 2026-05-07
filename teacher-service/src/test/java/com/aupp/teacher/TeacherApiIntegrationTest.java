package com.aupp.teacher;

import com.aupp.teacher.dto.CreateAssignmentRequest;
import com.aupp.teacher.repository.TeacherAssignmentRepository;
import com.aupp.teacher.web.CallerIdentity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "de.flapdoodle.mongodb.embedded.version=7.0.5")
class TeacherApiIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    TeacherAssignmentRepository repo;
    @Autowired
    ObjectMapper json;

    @BeforeEach
    void clean() {
        repo.deleteAll();
    }

    @Test
    void addAssignmentScopesByCallerEmail() throws Exception {
        CreateAssignmentRequest req = new CreateAssignmentRequest("Algebra exam", "Chapter 1-3", Instant.parse("2026-12-31T23:59:00Z"));
        mvc.perform(post("/addassignment")
                        .header(CallerIdentity.EMAIL_HEADER, "ms.smith@itc.edu.kh")
                        .header(CallerIdentity.ROLE_HEADER, "teacher")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.teacherEmail").value("ms.smith@itc.edu.kh"))
                .andExpect(jsonPath("$.title").value("Algebra exam"));
    }

    @Test
    void searchReturnsOnlyOwnAssignments() throws Exception {
        mvc.perform(post("/addassignment")
                        .header(CallerIdentity.EMAIL_HEADER, "ms.smith@itc.edu.kh")
                        .header(CallerIdentity.ROLE_HEADER, "teacher")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new CreateAssignmentRequest("Algebra", "x", null))))
                .andExpect(status().isCreated());
        mvc.perform(post("/addassignment")
                        .header(CallerIdentity.EMAIL_HEADER, "mr.doe@itc.edu.kh")
                        .header(CallerIdentity.ROLE_HEADER, "teacher")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new CreateAssignmentRequest("Geometry", "x", null))))
                .andExpect(status().isCreated());

        mvc.perform(get("/searchstudent")
                        .header(CallerIdentity.EMAIL_HEADER, "ms.smith@itc.edu.kh")
                        .header(CallerIdentity.ROLE_HEADER, "teacher"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.assignments[0].title").value("Algebra"));
    }

    @Test
    void cannotDeleteAnotherTeachersAssignment() throws Exception {
        mvc.perform(post("/addassignment")
                        .header(CallerIdentity.EMAIL_HEADER, "ms.smith@itc.edu.kh")
                        .header(CallerIdentity.ROLE_HEADER, "teacher")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new CreateAssignmentRequest("OnlyMine", "x", null))))
                .andExpect(status().isCreated());
        String foreignId = repo.findAll().get(0).getId();

        mvc.perform(delete("/removeassignment/" + foreignId)
                        .header(CallerIdentity.EMAIL_HEADER, "intruder@itc.edu.kh")
                        .header(CallerIdentity.ROLE_HEADER, "teacher"))
                .andExpect(status().isNotFound());
    }

    @Test
    void missingCallerIdentityRejected() throws Exception {
        mvc.perform(post("/addassignment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new CreateAssignmentRequest("x", "y", null))))
                .andExpect(status().isBadRequest());
    }
}
