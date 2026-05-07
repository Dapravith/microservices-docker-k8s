package com.aupp.student.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AssignmentTest {

    @Test
    void builderPopulatesAllFields() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        Assignment a = Assignment.builder()
                .id("1")
                .studentEmail("a@b.c")
                .title("HW")
                .content("body")
                .status("SUBMITTED")
                .createdAt(now)
                .updatedAt(now)
                .build();

        assertThat(a.getId()).isEqualTo("1");
        assertThat(a.getStudentEmail()).isEqualTo("a@b.c");
        assertThat(a.getTitle()).isEqualTo("HW");
        assertThat(a.getContent()).isEqualTo("body");
        assertThat(a.getStatus()).isEqualTo("SUBMITTED");
        assertThat(a.getCreatedAt()).isEqualTo(now);
        assertThat(a.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    void noArgConstructorAndSettersWork() {
        Assignment a = new Assignment();
        a.setId("x");
        a.setStudentEmail("e");
        a.setTitle("t");
        a.setContent("c");
        a.setStatus("DRAFT");
        a.setCreatedAt(Instant.EPOCH);
        a.setUpdatedAt(Instant.EPOCH);

        assertThat(a.getId()).isEqualTo("x");
        assertThat(a.getStudentEmail()).isEqualTo("e");
        assertThat(a.getTitle()).isEqualTo("t");
        assertThat(a.getContent()).isEqualTo("c");
        assertThat(a.getStatus()).isEqualTo("DRAFT");
        assertThat(a.getCreatedAt()).isEqualTo(Instant.EPOCH);
        assertThat(a.getUpdatedAt()).isEqualTo(Instant.EPOCH);
    }
}
