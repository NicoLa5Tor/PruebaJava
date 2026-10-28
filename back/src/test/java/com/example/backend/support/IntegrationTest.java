package com.example.backend.support;

import com.example.backend.auth.application.port.out.ExternalIdentityVerifierPort;
import com.example.backend.auth.infrastructure.token.JwtAccessTokenAdapter;
import com.example.backend.user.application.port.out.PasswordHasherPort;
import com.example.backend.user.application.port.out.UserRepositoryPort;
import com.example.backend.user.domain.model.User;
import com.example.backend.user.domain.model.UserRole;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base for integration tests: the real application (Flyway, JPA, EhCache, security) against PostgreSQL in a
 * container shared by every test class. Only the external Logto verifier is mocked.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "auth.jwt.secret=test-only-secret-with-at-least-thirty-two-characters",
        "auth.logto.issuer=https://logto.arisma.com.co/oidc",
        "auth.logto.app-id=wpqq5ute5og9jfags2mom",
        "app.seed.super-admin-email=",
        "app.seed.demo-users=false"
})
public abstract class IntegrationTest {

    @ServiceConnection
    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    static {
        POSTGRES.start();
    }

    @Autowired protected MockMvc mockMvc;
    @Autowired protected UserRepositoryPort users;
    @Autowired protected PasswordHasherPort passwordHasher;
    @Autowired protected JwtAccessTokenAdapter tokens;
    @MockitoBean protected ExternalIdentityVerifierPort identityVerifier;

    /** Emails must be unique across the shared database, so tests add a random suffix. */
    protected User createUser(String emailPrefix, UserRole... roles) {
        User user = User.newUser(emailPrefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com");
        user.setEmailVerified(true);
        user.setDisplayName(emailPrefix);
        user.setRoles(Set.of(roles));
        return users.save(user);
    }

    protected User createUserWithPassword(String emailPrefix, String password, UserRole... roles) {
        User user = createUser(emailPrefix, roles);
        user.setPasswordHash(passwordHasher.hash(password));
        return users.save(user);
    }

    protected String bearer(User user) {
        return "Bearer " + tokens.issue(user).value();
    }
}
