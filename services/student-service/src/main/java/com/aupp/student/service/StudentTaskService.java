package com.aupp.student.service;

import java.util.List;

import com.aupp.student.dto.SubmissionRequest;
import com.aupp.student.dto.SubmissionResponse;
import com.aupp.student.dto.TeacherTaskView;

public interface StudentTaskService {
    List<TeacherTaskView> listTeacherTasks(String studentEmail, String role);

    SubmissionResponse submit(String studentEmail, String role, SubmissionRequest request);

    List<SubmissionResponse> listSubmissions(String studentEmail, String role);
}
