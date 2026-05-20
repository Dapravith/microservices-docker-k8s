package com.aupp.teacher.controller;

import java.util.List;
import java.util.Map;

import com.aupp.teacher.dto.TaskRequest;
import com.aupp.teacher.dto.TaskResponse;
import com.aupp.teacher.model.TeacherTask;
import com.aupp.teacher.repository.TeacherTaskRepository;
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
@RequestMapping("/teacher")
public class TeacherTaskController {

    private final TeacherTaskRepository taskRepository;

    public TeacherTaskController(TeacherTaskRepository taskRepository) {
        this.taskRepository = taskRepository;
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
            @Valid @RequestBody TaskRequest request
    ) {
        if (teacherEmail.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "missing gateway identity header");
        }

        TeacherTask task = new TeacherTask(
                teacherEmail,
                request.title(),
                request.description(),
                request.course(),
                request.dueDate(),
                request.maxScore()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(TaskResponse.from(taskRepository.save(task)));
    }

    @GetMapping({"", "/tasks"})
    List<TaskResponse> listTeacherTasks(
            @RequestHeader(value = "X-User-Email", defaultValue = "") String teacherEmail
    ) {
        if (teacherEmail.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "missing gateway identity header");
        }
        return taskRepository.findByTeacherEmailOrderByCreatedAtDesc(teacherEmail)
                .stream()
                .map(TaskResponse::from)
                .toList();
    }
}
