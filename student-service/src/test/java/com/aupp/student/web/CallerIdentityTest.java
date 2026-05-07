package com.aupp.student.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CallerIdentityTest {

    @Test
    void isStudentMatchesCaseInsensitively() {
        assertThat(new CallerIdentity("a@b.c", "student").isStudent()).isTrue();
        assertThat(new CallerIdentity("a@b.c", "STUDENT").isStudent()).isTrue();
        assertThat(new CallerIdentity("a@b.c", "Student").isStudent()).isTrue();
    }

    @Test
    void isStudentReturnsFalseForOtherRoles() {
        assertThat(new CallerIdentity("a@b.c", "teacher").isStudent()).isFalse();
        assertThat(new CallerIdentity("a@b.c", null).isStudent()).isFalse();
        assertThat(new CallerIdentity("a@b.c", "").isStudent()).isFalse();
    }

    @Test
    void exposesHeaderConstants() {
        assertThat(CallerIdentity.EMAIL_HEADER).isEqualTo("X-User-Email");
        assertThat(CallerIdentity.ROLE_HEADER).isEqualTo("X-User-Role");
    }
}
