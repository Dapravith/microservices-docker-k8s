package com.aupp.teacher.dto;

import java.time.Instant;
import java.time.LocalDate;

import com.aupp.teacher.model.TeacherTask;

public record TaskResponse(
        String id,
        String teacherEmail,
        String title,
        String description,
        String course,
        LocalDate dueDate,
        int maxScore,
        Instant createdAt
) {
    public static TaskResponse from(TeacherTask task) {
        return new TaskResponse(
                task.getId(),
                task.getTeacherEmail(),
                task.getTitle(),
                task.getDescription(),
                task.getCourse(),
                task.getDueDate(),
                task.getMaxScore(),
                task.getCreatedAt()
        );
    }
}
