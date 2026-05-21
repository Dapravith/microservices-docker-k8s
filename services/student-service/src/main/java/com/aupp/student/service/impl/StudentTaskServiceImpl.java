package com.aupp.student.service.impl;

import java.util.List;
import java.util.Locale;

import com.aupp.student.client.TeacherTaskClient;
import com.aupp.student.dto.SubmissionRequest;
import com.aupp.student.dto.SubmissionResponse;
import com.aupp.student.dto.TeacherTaskView;
import com.aupp.student.exception.MissingGatewayIdentityException;
import com.aupp.student.model.Submission;
import com.aupp.student.repository.SubmissionRepository;
import com.aupp.student.service.StudentTaskService;
import org.springframework.stereotype.Service;

@Service
public class StudentTaskServiceImpl implements StudentTaskService {

    private final SubmissionRepository submissionRepository;
    private final TeacherTaskClient teacherTaskClient;

    public StudentTaskServiceImpl(SubmissionRepository submissionRepository, TeacherTaskClient teacherTaskClient) {
        this.submissionRepository = submissionRepository;
        this.teacherTaskClient = teacherTaskClient;
    }

    @Override
    public List<TeacherTaskView> listTeacherTasks() {
        return teacherTaskClient.listTasks();
    }

    @Override
    public SubmissionResponse submit(String studentEmail, SubmissionRequest request) {
        Submission submission = new Submission(
                requireStudentEmail(studentEmail),
                request.taskId().trim(),
                request.answer().trim()
        );
        return SubmissionResponse.from(submissionRepository.save(submission));
    }

    @Override
    public List<SubmissionResponse> listSubmissions(String studentEmail) {
        return submissionRepository.findByStudentEmailOrderBySubmittedAtDesc(requireStudentEmail(studentEmail))
                .stream()
                .map(SubmissionResponse::from)
                .toList();
    }

    private String requireStudentEmail(String studentEmail) {
        if (studentEmail == null || studentEmail.isBlank()) {
            throw new MissingGatewayIdentityException();
        }
        return studentEmail.trim().toLowerCase(Locale.ROOT);
    }
}
