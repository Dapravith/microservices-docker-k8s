package com.aupp.student.repository;

import com.aupp.student.domain.Assignment;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface AssignmentRepository extends MongoRepository<Assignment, String> {
    List<Assignment> findByStudentEmail(String studentEmail, Sort sort);
    Optional<Assignment> findFirstByStudentEmailOrderByCreatedAtDesc(String studentEmail);
}
