package com.aupp.student;

import com.aupp.student.dto.SubmitAssignmentRequest;
import com.aupp.student.dto.UpdateAssignmentRequest;
import com.aupp.student.repository.AssignmentRepository;
import com.aupp.student.web.CallerIdentity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "de.flapdoodle.mongodb.embedded.version=7.0.5",
        "spring.data.mongodb.uri=mongodb://localhost/test"
})
class AssignmentApiIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    AssignmentRepository repo;
    @Autowired
    ObjectMapper json;

    @BeforeEach
    void clean() {
        repo.deleteAll();
    }

    @Test
    void submitThenViewReturnsOnlyCallerOwnedAssignments() throws Exception {
        SubmitAssignmentRequest req = new SubmitAssignmentRequest("Math HW1", "Solve linear equations");
        mvc.perform(post("/submitassignment")
                        .header(CallerIdentity.EMAIL_HEADER, "alice@itc.edu.kh")
                        .header(CallerIdentity.ROLE_HEADER, "student")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("201"))
                .andExpect(jsonPath("$.message").value("Created"))
                .andExpect(jsonPath("$.data.studentEmail").value("alice@itc.edu.kh"))
                .andExpect(jsonPath("$.data.title").value("Math HW1"))
                .andExpect(jsonPath("$.pagination").doesNotExist());

        SubmitAssignmentRequest req2 = new SubmitAssignmentRequest("Other student work", "...");
        mvc.perform(post("/submitassignment")
                        .header(CallerIdentity.EMAIL_HEADER, "bob@itc.edu.kh")
                        .header(CallerIdentity.ROLE_HEADER, "student")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req2)))
                .andExpect(status().isCreated());

        mvc.perform(get("/viewassignment")
                        .header(CallerIdentity.EMAIL_HEADER, "alice@itc.edu.kh")
                        .header(CallerIdentity.ROLE_HEADER, "student"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].studentEmail").value("alice@itc.edu.kh"))
                .andExpect(jsonPath("$.pagination.page").value(1))
                .andExpect(jsonPath("$.pagination.size").value(1))
                .andExpect(jsonPath("$.pagination.total_counts").value(1))
                .andExpect(jsonPath("$.pagination.total_pages").value(1));
    }

    @Test
    void submitWithoutCallerIdentityReturns400() throws Exception {
        SubmitAssignmentRequest req = new SubmitAssignmentRequest("Math HW", "x");
        mvc.perform(post("/submitassignment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"));
    }

    @Test
    void updateLatestUpdatesMostRecentSubmission() throws Exception {
        mvc.perform(post("/submitassignment")
                        .header(CallerIdentity.EMAIL_HEADER, "alice@itc.edu.kh")
                        .header(CallerIdentity.ROLE_HEADER, "student")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new SubmitAssignmentRequest("HW1", "v1"))))
                .andExpect(status().isCreated());

        UpdateAssignmentRequest patch = new UpdateAssignmentRequest("HW1 (rev)", "v2");
        mvc.perform(put("/studentupdateprofile")
                        .header(CallerIdentity.EMAIL_HEADER, "alice@itc.edu.kh")
                        .header(CallerIdentity.ROLE_HEADER, "student")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(patch)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.title").value("HW1 (rev)"))
                .andExpect(jsonPath("$.data.content").value("v2"));
    }
}
