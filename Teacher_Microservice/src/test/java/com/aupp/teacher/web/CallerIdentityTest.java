package com.aupp.teacher.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CallerIdentityTest {

    @Test
    void isTeacherMatchesCaseInsensitively() {
        assertThat(new CallerIdentity("a@b.c", "teacher").isTeacher()).isTrue();
        assertThat(new CallerIdentity("a@b.c", "TEACHER").isTeacher()).isTrue();
    }

    @Test
    void isTeacherFalseForOtherRoles() {
        assertThat(new CallerIdentity("a@b.c", "student").isTeacher()).isFalse();
        assertThat(new CallerIdentity("a@b.c", null).isTeacher()).isFalse();
    }

    @Test
    void exposesHeaderConstants() {
        assertThat(CallerIdentity.EMAIL_HEADER).isEqualTo("X-User-Email");
        assertThat(CallerIdentity.ROLE_HEADER).isEqualTo("X-User-Role");
    }
}
