package com.aupp.student;

import static org.assertj.core.api.Assertions.assertThat;

import com.aupp.student.dto.SubmissionResponse;
import com.aupp.student.model.Submission;
import org.junit.jupiter.api.Test;

class SubmissionResponseTest {

    @Test
    void mapsSubmissionToApiResponse() {
        Submission submission = new Submission(
                "student1@aupp.edu",
                "task-1",
                "My Kubernetes deployment is complete."
        );
        submission.setId("submission-1");

        SubmissionResponse response = SubmissionResponse.from(submission);

        assertThat(response.id()).isEqualTo("submission-1");
        assertThat(response.studentEmail()).isEqualTo("student1@aupp.edu");
        assertThat(response.status()).isEqualTo("SUBMITTED");
    }
}
