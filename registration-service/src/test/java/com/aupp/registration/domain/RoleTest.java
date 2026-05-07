package com.aupp.registration.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoleTest {

    @Test
    void fromAcceptsCommonCasings() {
        assertThat(Role.from("student")).isEqualTo(Role.STUDENT);
        assertThat(Role.from("TEACHER")).isEqualTo(Role.TEACHER);
        assertThat(Role.from(" Teacher ")).isEqualTo(Role.TEACHER);
    }

    @Test
    void fromRejectsNullAndUnknown() {
        assertThatThrownBy(() -> Role.from(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Role.from("admin")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lowerReturnsLowercaseName() {
        assertThat(Role.STUDENT.lower()).isEqualTo("student");
        assertThat(Role.TEACHER.lower()).isEqualTo("teacher");
    }
}
