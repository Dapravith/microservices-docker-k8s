package com.aupp.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JwtRoleAuthFilterTest {

    private static final String SECRET = "test-secret-key-with-at-least-32-bytes-for-hs256";
    private final JwtRoleAuthFilter filter = new JwtRoleAuthFilter(new JwtVerifier(SECRET));

    @Test
    void mapsStudentAndTeacherPathsToRequiredRoles() {
        assertThat(filter.requiredRole("/student")).isEqualTo("STUDENT");
        assertThat(filter.requiredRole("/student/submissions")).isEqualTo("STUDENT");
        assertThat(filter.requiredRole("/teacher")).isEqualTo("TEACHER");
        assertThat(filter.requiredRole("/teacher/tasks")).isEqualTo("TEACHER");
        assertThat(filter.requiredRole("/unknown")).isNull();
    }

    @Test
    void leavesAuthAndActuatorPublic() {
        assertThat(filter.isPublicPath("/auth/login")).isTrue();
        assertThat(filter.isPublicPath("/actuator/health")).isTrue();
        assertThat(filter.isPublicPath("/student")).isFalse();
    }
}
