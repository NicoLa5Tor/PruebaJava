package com.example.backend;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.backend.dto.user.CreateUserRequest;
import com.example.backend.dto.user.UpdateUserRequest;
import com.example.backend.dto.user.UserResponse;
import com.example.backend.service.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "auth.jwt.secret=test-only-secret-with-at-least-thirty-two-characters",
        "auth.logto.issuer=https://logto.arisma.com.co/oidc",
        "auth.logto.app-id=wpqq5ute5og9jfags2mom"
})
class BackendApplicationIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private UserService userService;

    @Test
    void contextLoads() {
        // The context connects to a real PostgreSQL instance managed by Testcontainers.
    }

    @Test
    void createsUpdatesAndDeletesUser() {
        UserResponse created = userService.create(new CreateUserRequest(
                "person@example.com", "Ada", "Lovelace", "Ada", null, "logto-user-1", null
        ));

        UserResponse updated = userService.update(created.id(), new UpdateUserRequest(
                null, null, null, "Ada Lovelace", null, null, true, null, null
        ));

        assertThat(updated.email()).isEqualTo("person@example.com");
        assertThat(updated.displayName()).isEqualTo("Ada Lovelace");
        assertThat(updated.emailVerified()).isTrue();
        assertThat(userService.findAll(org.springframework.data.domain.Pageable.unpaged()).totalElements()).isEqualTo(2);

        userService.delete(created.id());

        assertThat(userService.findAll(org.springframework.data.domain.Pageable.unpaged()).totalElements()).isEqualTo(1);
    }
}
