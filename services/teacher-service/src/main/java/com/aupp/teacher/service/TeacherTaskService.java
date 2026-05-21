package com.aupp.teacher.service;

import java.util.List;

import com.aupp.teacher.dto.TaskRequest;
import com.aupp.teacher.dto.TaskResponse;

public interface TeacherTaskService {
    TaskResponse createTask(String teacherEmail, TaskRequest request);

    List<TaskResponse> listTeacherTasks(String teacherEmail);

    List<TaskResponse> listTasksForStudents();
}
