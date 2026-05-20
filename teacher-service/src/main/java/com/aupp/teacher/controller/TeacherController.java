package com.aupp.teacher.controller;

import com.aupp.teacher.dto.TeacherRequest;
import com.aupp.teacher.model.Teacher;
import com.aupp.teacher.repository.TeacherRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/teacher")
public class TeacherController {

    private final TeacherRepository repo;

    public TeacherController(TeacherRepository repo) {
        this.repo = repo;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("service", "teacher-service", "status", "UP");
    }

    @GetMapping("/me")
    public Map<String, String> me(@RequestHeader(value = "X-User-Email", defaultValue = "") String email,
                                  @RequestHeader(value = "X-User-Role", defaultValue = "") String role) {
        return Map.of(
                "service", "teacher-service",
                "email", email,
                "role", role
        );
    }

    @GetMapping
    public List<Teacher> list(@RequestHeader(value = "X-User-Email", defaultValue = "") String email) {
        return email.isBlank() ? repo.findAll() : repo.findByOwnerEmail(email);
    }

    @PostMapping
    public ResponseEntity<Teacher> create(@Valid @RequestBody TeacherRequest req,
                                          @RequestHeader(value = "X-User-Email", defaultValue = "anonymous") String email) {
        Teacher saved = repo.save(Teacher.builder()
                .ownerEmail(email)
                .name(req.getName())
                .department(req.getDepartment())
                .courses(req.getCourses())
                .yearsOfExperience(req.getYearsOfExperience())
                .build());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping("/{id}")
    public Teacher get(@PathVariable String id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "teacher not found"));
    }

    @PutMapping("/{id}")
    public Teacher update(@PathVariable String id, @Valid @RequestBody TeacherRequest req) {
        Teacher existing = repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "teacher not found"));
        existing.setName(req.getName());
        existing.setDepartment(req.getDepartment());
        existing.setCourses(req.getCourses());
        existing.setYearsOfExperience(req.getYearsOfExperience());
        return repo.save(existing);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        if (!repo.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "teacher not found");
        }
        repo.deleteById(id);
    }
}
