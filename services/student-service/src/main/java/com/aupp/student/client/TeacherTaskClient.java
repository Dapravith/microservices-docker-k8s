package com.aupp.student.client;

import java.util.List;

import com.aupp.student.dto.TeacherTaskView;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class TeacherTaskClient {

    private static final ParameterizedTypeReference<List<TeacherTaskView>> TASK_LIST_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;

    public TeacherTaskClient(@Value("${teacher.service-uri}") String teacherServiceUri) {
        this.restClient = RestClient.builder()
                .baseUrl(teacherServiceUri)
                .build();
    }

    public List<TeacherTaskView> listTasks() {
        List<TeacherTaskView> tasks = restClient.get()
                .uri("/internal/tasks")
                .retrieve()
                .body(TASK_LIST_TYPE);
        return tasks == null ? List.of() : tasks;
    }
}
