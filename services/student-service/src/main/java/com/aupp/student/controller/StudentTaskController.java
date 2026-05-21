package com.aupp.student.controller;

import java.util.List;
import java.util.Map;

import com.aupp.student.dto.SubmissionRequest;
import com.aupp.student.dto.SubmissionResponse;
import com.aupp.student.dto.TeacherTaskView;
import com.aupp.student.service.StudentTaskService;
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
@RequestMapping("/student")
public class StudentTaskController {

    private final StudentTaskService studentTaskService;

    public StudentTaskController(StudentTaskService studentTaskService) {
        this.studentTaskService = studentTaskService;
    }

    @GetMapping("/me")
    Map<String, String> me(
            @RequestHeader(value = "X-User-Email", defaultValue = "") String email,
            @RequestHeader(value = "X-User-Role", defaultValue = "") String role
    ) {
        return Map.of("service", "student-service", "email", email, "role", role);
    }

    @GetMapping("/tasks")
    List<TeacherTaskView> listTasks() {
        return studentTaskService.listTeacherTasks();
    }

    @PostMapping({"", "/submissions"})
    ResponseEntity<SubmissionResponse> submit(
            @RequestHeader(value = "X-User-Email", defaultValue = "") String studentEmail,
            @Valid @RequestBody SubmissionRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(studentTaskService.submit(studentEmail, request));
    }

    @GetMapping({"", "/submissions"})
    List<SubmissionResponse> listSubmissions(
            @RequestHeader(value = "X-User-Email", defaultValue = "") String studentEmail
    ) {
        return studentTaskService.listSubmissions(studentEmail);
    }
}
