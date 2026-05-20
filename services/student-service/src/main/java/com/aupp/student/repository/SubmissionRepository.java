package com.aupp.student.repository;

import java.util.List;

import com.aupp.student.model.Submission;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface SubmissionRepository extends MongoRepository<Submission, String> {

    List<Submission> findByStudentEmailOrderBySubmittedAtDesc(String studentEmail);
}
