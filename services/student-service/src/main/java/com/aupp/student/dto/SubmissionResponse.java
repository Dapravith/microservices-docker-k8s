package com.aupp.student.dto;

import java.time.Instant;

import com.aupp.student.model.Submission;

public record SubmissionResponse(
        String id,
        String studentEmail,
        String taskId,
        String answer,
        String status,
        Instant submittedAt
) {
    public static SubmissionResponse from(Submission submission) {
        return new SubmissionResponse(
                submission.getId(),
                submission.getStudentEmail(),
                submission.getTaskId(),
                submission.getAnswer(),
                submission.getStatus(),
                submission.getSubmittedAt()
        );
    }
}
