package com.aupp.student.repository;

import com.aupp.student.model.Student;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface StudentRepository extends MongoRepository<Student, String> {
    List<Student> findByOwnerEmail(String ownerEmail);
}
