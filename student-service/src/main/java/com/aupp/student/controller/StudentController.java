package com.aupp.student.controller;

import com.aupp.student.dto.StudentRequest;
import com.aupp.student.model.Student;
import com.aupp.student.repository.StudentRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/student")
public class StudentController {

    private final StudentRepository repo;

    public StudentController(StudentRepository repo) {
        this.repo = repo;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("service", "student-service", "status", "UP");
    }

    /**
     * "Whoami" — useful screenshot endpoint that proves the JWT carried by the
     * gateway resulted in the right identity reaching this service.
     */
    @GetMapping("/me")
    public Map<String, String> me(@RequestHeader(value = "X-User-Email", defaultValue = "") String email,
                                  @RequestHeader(value = "X-User-Role", defaultValue = "") String role) {
        return Map.of(
                "service", "student-service",
                "email", email,
                "role", role
        );
    }

    @GetMapping
    public List<Student> list(@RequestHeader(value = "X-User-Email", defaultValue = "") String email) {
        // Each student sees their own records; if no header, return everything (gateway always sets it in prod).
        return email.isBlank() ? repo.findAll() : repo.findByOwnerEmail(email);
    }

    @PostMapping
    public ResponseEntity<Student> create(@Valid @RequestBody StudentRequest req,
                                          @RequestHeader(value = "X-User-Email", defaultValue = "anonymous") String email) {
        Student saved = repo.save(Student.builder()
                .ownerEmail(email)
                .name(req.getName())
                .major(req.getMajor())
                .year(req.getYear())
                .gpa(req.getGpa())
                .build());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping("/{id}")
    public Student get(@PathVariable String id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "student not found"));
    }

    @PutMapping("/{id}")
    public Student update(@PathVariable String id, @Valid @RequestBody StudentRequest req) {
        Student existing = repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "student not found"));
        existing.setName(req.getName());
        existing.setMajor(req.getMajor());
        existing.setYear(req.getYear());
        existing.setGpa(req.getGpa());
        return repo.save(existing);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        if (!repo.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "student not found");
        }
        repo.deleteById(id);
    }
}
