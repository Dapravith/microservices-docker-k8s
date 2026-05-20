package com.aupp.teacher.repository;

import java.util.List;

import com.aupp.teacher.model.TeacherTask;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TeacherTaskRepository extends MongoRepository<TeacherTask, String> {

    List<TeacherTask> findByTeacherEmailOrderByCreatedAtDesc(String teacherEmail);

    List<TeacherTask> findAllByOrderByCreatedAtDesc();
}
