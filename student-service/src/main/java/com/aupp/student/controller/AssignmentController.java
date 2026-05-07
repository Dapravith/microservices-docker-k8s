package com.aupp.student.controller;

import com.aupp.student.dto.AssignmentListResponse;
import com.aupp.student.dto.AssignmentResponse;
import com.aupp.student.dto.SubmitAssignmentRequest;
import com.aupp.student.dto.UpdateAssignmentRequest;
import com.aupp.student.service.AssignmentService;
import com.aupp.student.web.CallerIdentity;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AssignmentController {

    private final AssignmentService service;

    public AssignmentController(AssignmentService service) {
        this.service = service;
    }

    @PostMapping("/submitassignment")
    public ResponseEntity<AssignmentResponse> submit(
            @RequestHeader(value = CallerIdentity.EMAIL_HEADER, required = false) String email,
            @RequestHeader(value = CallerIdentity.ROLE_HEADER, required = false) String role,
            @Valid @RequestBody SubmitAssignmentRequest req) {
        AssignmentResponse created = service.submit(new CallerIdentity(email, role), req);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/viewassignment")
    public AssignmentListResponse view(
            @RequestHeader(value = CallerIdentity.EMAIL_HEADER, required = false) String email,
            @RequestHeader(value = CallerIdentity.ROLE_HEADER, required = false) String role) {
        return service.listMine(new CallerIdentity(email, role));
    }

    @PutMapping("/studentupdateprofile")
    public AssignmentResponse update(
            @RequestHeader(value = CallerIdentity.EMAIL_HEADER, required = false) String email,
            @RequestHeader(value = CallerIdentity.ROLE_HEADER, required = false) String role,
            @Valid @RequestBody UpdateAssignmentRequest req) {
        return service.updateLatest(new CallerIdentity(email, role), req);
    }

    @PutMapping("/studentresubmitassignment")
    public AssignmentResponse resubmit(
            @RequestHeader(value = CallerIdentity.EMAIL_HEADER, required = false) String email,
            @RequestHeader(value = CallerIdentity.ROLE_HEADER, required = false) String role,
            @Valid @RequestBody UpdateAssignmentRequest req) {
        return service.resubmitLatest(new CallerIdentity(email, role), req);
    }
}
