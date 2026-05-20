package com.aupp.teacher.repository;

import com.aupp.teacher.domain.TeacherAssignment;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface TeacherAssignmentRepository extends MongoRepository<TeacherAssignment, String> {
    List<TeacherAssignment> findByTeacherEmail(String teacherEmail, Sort sort);
    List<TeacherAssignment> findByTeacherEmailAndTitleRegex(String teacherEmail, String titleRegex, Sort sort);
    Optional<TeacherAssignment> findByIdAndTeacherEmail(String id, String teacherEmail);
}
