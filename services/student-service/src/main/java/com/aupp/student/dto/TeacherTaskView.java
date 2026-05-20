package com.aupp.student.dto;

import java.time.Instant;
import java.time.LocalDate;

public record TeacherTaskView(
        String id,
        String teacherEmail,
        String title,
        String description,
        String course,
        LocalDate dueDate,
        int maxScore,
        Instant createdAt
) {
}
