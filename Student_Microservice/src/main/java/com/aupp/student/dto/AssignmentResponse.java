package com.aupp.student.dto;

import com.aupp.student.domain.Assignment;

import java.time.Instant;

public record AssignmentResponse(
        String id,
        String studentEmail,
        String title,
        String content,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
    public static AssignmentResponse of(Assignment a) {
        return new AssignmentResponse(
                a.getId(),
                a.getStudentEmail(),
                a.getTitle(),
                a.getContent(),
                a.getStatus() == null ? null : a.getStatus().name(),
                a.getCreatedAt(),
                a.getUpdatedAt()
        );
    }
}
