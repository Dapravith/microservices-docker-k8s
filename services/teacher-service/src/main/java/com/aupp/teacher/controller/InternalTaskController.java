package com.aupp.teacher.controller;

import java.util.List;

import com.aupp.teacher.dto.TaskResponse;
import com.aupp.teacher.service.TeacherTaskService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InternalTaskController {

    private final TeacherTaskService taskService;

    public InternalTaskController(TeacherTaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping("/internal/tasks")
    List<TaskResponse> listTasksForStudents() {
        return taskService.listTasksForStudents();
    }
}
