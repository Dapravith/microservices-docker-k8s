package com.aupp.teacher.model;

import java.time.Instant;
import java.time.LocalDate;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "teacher_tasks")
public class TeacherTask {

    @Id
    private String id;

    @Indexed
    private String teacherEmail;

    private String title;
    private String description;
    private String course;
    private LocalDate dueDate;
    private int maxScore;
    private Instant createdAt = Instant.now();

    public TeacherTask() {
    }

    public TeacherTask(String teacherEmail, String title, String description, String course, LocalDate dueDate, int maxScore) {
        this.teacherEmail = teacherEmail;
        this.title = title;
        this.description = description;
        this.course = course;
        this.dueDate = dueDate;
        this.maxScore = maxScore;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTeacherEmail() {
        return teacherEmail;
    }

    public void setTeacherEmail(String teacherEmail) {
        this.teacherEmail = teacherEmail;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCourse() {
        return course;
    }

    public void setCourse(String course) {
        this.course = course;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public int getMaxScore() {
        return maxScore;
    }

    public void setMaxScore(int maxScore) {
        this.maxScore = maxScore;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
