package com.aupp.teacher.controller;

import java.util.List;
import java.util.Map;

import com.aupp.teacher.dto.TaskRequest;
import com.aupp.teacher.dto.TaskResponse;
import com.aupp.teacher.service.TeacherTaskService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/teacher")
public class TeacherTaskController {

    private final TeacherTaskService taskService;

    public TeacherTaskController(TeacherTaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping("/me")
    Map<String, String> me(
            @RequestHeader(value = "X-User-Email", defaultValue = "") String email,
            @RequestHeader(value = "X-User-Role", defaultValue = "") String role
    ) {
        return Map.of("service", "teacher-service", "email", email, "role", role);
    }

    @PostMapping({"", "/tasks"})
    ResponseEntity<TaskResponse> createTask(
            @RequestHeader(value = "X-User-Email", defaultValue = "") String teacherEmail,
            @RequestHeader(value = "X-User-Role", defaultValue = "") String role,
            @Valid @RequestBody TaskRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(taskService.createTask(teacherEmail, role, request));
    }

    @GetMapping({"", "/tasks"})
    List<TaskResponse> listTeacherTasks(
            @RequestHeader(value = "X-User-Email", defaultValue = "") String teacherEmail,
            @RequestHeader(value = "X-User-Role", defaultValue = "") String role
    ) {
        return taskService.listTeacherTasks(teacherEmail, role);
    }
}
