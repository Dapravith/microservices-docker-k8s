package com.aupp.student.service.impl;

import java.util.List;
import java.util.Locale;

import com.aupp.student.client.TeacherTaskClient;
import com.aupp.student.dto.SubmissionRequest;
import com.aupp.student.dto.SubmissionResponse;
import com.aupp.student.dto.TeacherTaskView;
import com.aupp.student.exception.ForbiddenGatewayRoleException;
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
    public List<TeacherTaskView> listTeacherTasks(String studentEmail, String role) {
        requireStudentContext(studentEmail, role);
        return teacherTaskClient.listTasks();
    }

    @Override
    public SubmissionResponse submit(String studentEmail, String role, SubmissionRequest request) {
        Submission submission = new Submission(
                requireStudentContext(studentEmail, role),
                request.taskId().trim(),
                request.answer().trim()
        );
        return SubmissionResponse.from(submissionRepository.save(submission));
    }

    @Override
    public List<SubmissionResponse> listSubmissions(String studentEmail, String role) {
        return submissionRepository.findByStudentEmailOrderBySubmittedAtDesc(requireStudentContext(studentEmail, role))
                .stream()
                .map(SubmissionResponse::from)
                .toList();
    }

    private String requireStudentContext(String studentEmail, String role) {
        String normalizedEmail = requireStudentEmail(studentEmail);
        requireStudentRole(role);
        return normalizedEmail;
    }

    private String requireStudentEmail(String studentEmail) {
        if (studentEmail == null || studentEmail.isBlank()) {
            throw new MissingGatewayIdentityException();
        }
        return studentEmail.trim().toLowerCase(Locale.ROOT);
    }

    private void requireStudentRole(String role) {
        if (role == null || role.isBlank()) {
            throw new MissingGatewayIdentityException();
        }
        if (!"STUDENT".equalsIgnoreCase(role.trim())) {
            throw new ForbiddenGatewayRoleException("STUDENT", role);
        }
    }
}
