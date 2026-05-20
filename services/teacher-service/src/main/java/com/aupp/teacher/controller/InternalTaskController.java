package com.aupp.teacher.controller;

import java.util.List;

import com.aupp.teacher.dto.TaskResponse;
import com.aupp.teacher.repository.TeacherTaskRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InternalTaskController {

    private final TeacherTaskRepository taskRepository;

    public InternalTaskController(TeacherTaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @GetMapping("/internal/tasks")
    List<TaskResponse> listTasksForStudents() {
        return taskRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(TaskResponse::from)
                .toList();
    }
}
