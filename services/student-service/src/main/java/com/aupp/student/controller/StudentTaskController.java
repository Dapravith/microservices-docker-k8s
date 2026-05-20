package com.aupp.student.controller;

import java.util.List;
import java.util.Map;

import com.aupp.student.client.TeacherTaskClient;
import com.aupp.student.dto.SubmissionRequest;
import com.aupp.student.dto.SubmissionResponse;
import com.aupp.student.dto.TeacherTaskView;
import com.aupp.student.model.Submission;
import com.aupp.student.repository.SubmissionRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/student")
public class StudentTaskController {

    private final SubmissionRepository submissionRepository;
    private final TeacherTaskClient teacherTaskClient;

    public StudentTaskController(SubmissionRepository submissionRepository, TeacherTaskClient teacherTaskClient) {
        this.submissionRepository = submissionRepository;
        this.teacherTaskClient = teacherTaskClient;
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
        return teacherTaskClient.listTasks();
    }

    @PostMapping({"", "/submissions"})
    ResponseEntity<SubmissionResponse> submit(
            @RequestHeader(value = "X-User-Email", defaultValue = "") String studentEmail,
            @Valid @RequestBody SubmissionRequest request
    ) {
        if (studentEmail.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "missing gateway identity header");
        }
        Submission saved = submissionRepository.save(new Submission(studentEmail, request.taskId(), request.answer()));
        return ResponseEntity.status(HttpStatus.CREATED).body(SubmissionResponse.from(saved));
    }

    @GetMapping({"", "/submissions"})
    List<SubmissionResponse> listSubmissions(
            @RequestHeader(value = "X-User-Email", defaultValue = "") String studentEmail
    ) {
        if (studentEmail.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "missing gateway identity header");
        }
        return submissionRepository.findByStudentEmailOrderBySubmittedAtDesc(studentEmail)
                .stream()
                .map(SubmissionResponse::from)
                .toList();
    }
}
