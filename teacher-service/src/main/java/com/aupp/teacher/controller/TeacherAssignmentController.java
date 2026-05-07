package com.aupp.teacher.controller;

import com.aupp.teacher.dto.AssignmentListResponse;
import com.aupp.teacher.dto.AssignmentResponse;
import com.aupp.teacher.dto.CreateAssignmentRequest;
import com.aupp.teacher.service.TeacherAssignmentService;
import com.aupp.teacher.web.CallerIdentity;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class TeacherAssignmentController {

    private final TeacherAssignmentService service;

    public TeacherAssignmentController(TeacherAssignmentService service) {
        this.service = service;
    }

    @PostMapping("/addassignment")
    public ResponseEntity<AssignmentResponse> add(
            @RequestHeader(value = CallerIdentity.EMAIL_HEADER, required = false) String email,
            @RequestHeader(value = CallerIdentity.ROLE_HEADER, required = false) String role,
            @Valid @RequestBody CreateAssignmentRequest req) {
        AssignmentResponse created = service.create(new CallerIdentity(email, role), req);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/searchstudent")
    public AssignmentListResponse search(
            @RequestHeader(value = CallerIdentity.EMAIL_HEADER, required = false) String email,
            @RequestHeader(value = CallerIdentity.ROLE_HEADER, required = false) String role,
            @RequestParam(value = "title", required = false) String title) {
        return service.search(new CallerIdentity(email, role), title);
    }

    @DeleteMapping("/removeassignment/{id}")
    public Map<String, String> remove(
            @RequestHeader(value = CallerIdentity.EMAIL_HEADER, required = false) String email,
            @RequestHeader(value = CallerIdentity.ROLE_HEADER, required = false) String role,
            @PathVariable("id") String id) {
        service.remove(new CallerIdentity(email, role), id);
        return Map.of("message", "Assignment removed", "id", id);
    }
}
