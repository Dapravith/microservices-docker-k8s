package com.aupp.teacher.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TeacherAssignmentTest {

    @Test
    void builderPopulatesAllFields() {
        Instant due = Instant.parse("2026-12-31T23:59:00Z");
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        TeacherAssignment a = TeacherAssignment.builder()
                .id("1")
                .teacherEmail("ms@x.y")
                .title("Algebra")
                .description("Ch1")
                .dueDate(due)
                .createdAt(now)
                .updatedAt(now)
                .build();

        assertThat(a.getId()).isEqualTo("1");
        assertThat(a.getTeacherEmail()).isEqualTo("ms@x.y");
        assertThat(a.getTitle()).isEqualTo("Algebra");
        assertThat(a.getDescription()).isEqualTo("Ch1");
        assertThat(a.getDueDate()).isEqualTo(due);
        assertThat(a.getCreatedAt()).isEqualTo(now);
        assertThat(a.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    void noArgConstructorAndSettersWork() {
        TeacherAssignment a = new TeacherAssignment();
        a.setId("x");
        a.setTeacherEmail("e");
        a.setTitle("t");
        a.setDescription("d");
        a.setDueDate(Instant.EPOCH);
        a.setCreatedAt(Instant.EPOCH);
        a.setUpdatedAt(Instant.EPOCH);

        assertThat(a.getId()).isEqualTo("x");
        assertThat(a.getTeacherEmail()).isEqualTo("e");
        assertThat(a.getTitle()).isEqualTo("t");
        assertThat(a.getDescription()).isEqualTo("d");
        assertThat(a.getDueDate()).isEqualTo(Instant.EPOCH);
        assertThat(a.getCreatedAt()).isEqualTo(Instant.EPOCH);
        assertThat(a.getUpdatedAt()).isEqualTo(Instant.EPOCH);
    }
}
