package com.aupp.teacher;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import com.aupp.teacher.dto.TaskResponse;
import com.aupp.teacher.model.TeacherTask;
import org.junit.jupiter.api.Test;

class TaskResponseTest {

    @Test
    void mapsTeacherTaskToApiResponse() {
        TeacherTask task = new TeacherTask(
                "teacher1@aupp.edu",
                "Kubernetes Lab",
                "Deploy the services locally.",
                "Cloud Computing",
                LocalDate.now().plusDays(7),
                100
        );
        task.setId("task-1");

        TaskResponse response = TaskResponse.from(task);

        assertThat(response.id()).isEqualTo("task-1");
        assertThat(response.teacherEmail()).isEqualTo("teacher1@aupp.edu");
        assertThat(response.title()).isEqualTo("Kubernetes Lab");
    }
}
