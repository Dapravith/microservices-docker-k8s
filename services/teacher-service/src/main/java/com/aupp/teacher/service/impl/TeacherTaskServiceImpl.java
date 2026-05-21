package com.aupp.teacher.service.impl;

import java.util.List;
import java.util.Locale;

import com.aupp.teacher.dto.TaskRequest;
import com.aupp.teacher.dto.TaskResponse;
import com.aupp.teacher.exception.ForbiddenGatewayRoleException;
import com.aupp.teacher.exception.MissingGatewayIdentityException;
import com.aupp.teacher.model.TeacherTask;
import com.aupp.teacher.repository.TeacherTaskRepository;
import com.aupp.teacher.service.TeacherTaskService;
import org.springframework.stereotype.Service;

@Service
public class TeacherTaskServiceImpl implements TeacherTaskService {

    private final TeacherTaskRepository taskRepository;

    public TeacherTaskServiceImpl(TeacherTaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Override
    public TaskResponse createTask(String teacherEmail, String role, TaskRequest request) {
        TeacherTask task = new TeacherTask(
                requireTeacherContext(teacherEmail, role),
                request.title().trim(),
                request.description().trim(),
                request.course().trim(),
                request.dueDate(),
                request.maxScore()
        );
        return TaskResponse.from(taskRepository.save(task));
    }

    @Override
    public List<TaskResponse> listTeacherTasks(String teacherEmail, String role) {
        return taskRepository.findByTeacherEmailOrderByCreatedAtDesc(requireTeacherContext(teacherEmail, role))
                .stream()
                .map(TaskResponse::from)
                .toList();
    }

    @Override
    public List<TaskResponse> listTasksForStudents() {
        return taskRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(TaskResponse::from)
                .toList();
    }

    private String requireTeacherContext(String teacherEmail, String role) {
        String normalizedEmail = requireTeacherEmail(teacherEmail);
        requireTeacherRole(role);
        return normalizedEmail;
    }

    private String requireTeacherEmail(String teacherEmail) {
        if (teacherEmail == null || teacherEmail.isBlank()) {
            throw new MissingGatewayIdentityException();
        }
        return teacherEmail.trim().toLowerCase(Locale.ROOT);
    }

    private void requireTeacherRole(String role) {
        if (role == null || role.isBlank()) {
            throw new MissingGatewayIdentityException();
        }
        if (!"TEACHER".equalsIgnoreCase(role.trim())) {
            throw new ForbiddenGatewayRoleException("TEACHER", role);
        }
    }
}
