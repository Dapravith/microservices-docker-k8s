package com.aupp.gateway;

import com.aupp.gateway.security.JwtRoleAuthFilter;
import com.aupp.gateway.security.JwtVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
        "app.jwt.secret=test-secret-please-change-me-1234567890-abcdef",
        "spring.cloud.gateway.routes[0].id=login-route",
        "spring.cloud.gateway.routes[0].uri=http://localhost:5002",
        "spring.cloud.gateway.routes[0].predicates[0]=Path=/login"
})
class ApiGatewayApplicationTest {

    @Autowired JwtVerifier verifier;
    @Autowired JwtRoleAuthFilter filter;

    @Test
    void contextLoadsAndExposesSecurityBeans() {
        assertThat(verifier).isNotNull();
        assertThat(filter).isNotNull();
    }
}
