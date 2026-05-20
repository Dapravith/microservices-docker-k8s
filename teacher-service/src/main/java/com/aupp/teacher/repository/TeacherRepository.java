package com.aupp.teacher.repository;

import com.aupp.teacher.model.Teacher;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface TeacherRepository extends MongoRepository<Teacher, String> {
    List<Teacher> findByOwnerEmail(String ownerEmail);
}
