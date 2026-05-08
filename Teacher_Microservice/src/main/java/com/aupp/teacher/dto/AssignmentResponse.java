package com.aupp.teacher.dto;

import com.aupp.teacher.domain.TeacherAssignment;

import java.time.Instant;

public record AssignmentResponse(
        String id,
        String teacherEmail,
        String title,
        String description,
        Instant dueDate,
        Instant createdAt,
        Instant updatedAt
) {
    public static AssignmentResponse of(TeacherAssignment a) {
        return new AssignmentResponse(
                a.getId(),
                a.getTeacherEmail(),
                a.getTitle(),
                a.getDescription(),
                a.getDueDate(),
                a.getCreatedAt(),
                a.getUpdatedAt()
        );
    }
}
