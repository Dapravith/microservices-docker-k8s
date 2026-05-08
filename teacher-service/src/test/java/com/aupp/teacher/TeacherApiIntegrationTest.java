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
@TestPropertySource(properties = {
        "de.flapdoodle.mongodb.embedded.version=7.0.5",
        "spring.data.mongodb.uri=mongodb://localhost/test"
})
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
                .andExpect(jsonPath("$.code").value("201"))
                .andExpect(jsonPath("$.message").value("Created"))
                .andExpect(jsonPath("$.data.teacherEmail").value("ms.smith@itc.edu.kh"))
                .andExpect(jsonPath("$.data.title").value("Algebra exam"))
                .andExpect(jsonPath("$.pagination").doesNotExist());
    }

    @Test
    void searchReturnsOnlyOwnAssignmentsAndIncludesPagination() throws Exception {
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
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].title").value("Algebra"))
                .andExpect(jsonPath("$.pagination.page").value(1))
                .andExpect(jsonPath("$.pagination.size").value(1))
                .andExpect(jsonPath("$.pagination.total_counts").value(1))
                .andExpect(jsonPath("$.pagination.total_pages").value(1));
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
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("404"));
    }

    @Test
    void missingCallerIdentityRejected() throws Exception {
        mvc.perform(post("/addassignment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new CreateAssignmentRequest("x", "y", null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"));
    }
}
