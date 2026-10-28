package com.example.backend.user.infrastructure.seed;

import com.example.backend.user.application.port.out.PasswordHasherPort;
import com.example.backend.user.application.port.out.UserRepositoryPort;
import com.example.backend.user.domain.model.User;
import com.example.backend.user.domain.model.UserRole;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Makes sure the configured super admin exists and, for demos, creates the accounts listed in the exercise.
 * Writes the password hash directly, so the demo password "123" skips the API's minimum length rule.
 */
@Component
class UserSeed implements ApplicationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserSeed.class);
    static final String DEMO_PASSWORD = "123";

    private final UserRepositoryPort users;
    private final PasswordHasherPort passwordHasher;
    private final SeedProperties properties;

    UserSeed(UserRepositoryPort users, PasswordHasherPort passwordHasher, SeedProperties properties) {
        this.users = users;
        this.passwordHasher = passwordHasher;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (properties.superAdminEmail() != null && !properties.superAdminEmail().isBlank()) {
            ensureUser(properties.superAdminEmail(), "Super Admin", UserRole.SUPER_ADMIN, properties.superAdminPassword());
        }
        if (properties.demoUsers()) {
            ensureUser("usuario@test.com", "Usuario", UserRole.USER, DEMO_PASSWORD);
            ensureUser("admin@test.com", "Admin Admin", UserRole.ADMIN, DEMO_PASSWORD);
            ensureUser("superadmin@test.com", "Super Admin", UserRole.SUPER_ADMIN, DEMO_PASSWORD);
            LOGGER.warn("Demo users are enabled (password '{}'). Set SEED_DEMO_USERS=false outside demos.", DEMO_PASSWORD);
        }
    }

    /** Creates the account if missing; an existing one keeps its data and only gains the role (and a password if it had none). */
    private void ensureUser(String email, String displayName, UserRole role, String password) {
        User user = users.findByEmail(User.normalizeEmail(email)).orElseGet(() -> {
            User created = User.newUser(email);
            created.setDisplayName(displayName);
            created.setEmailVerified(true);
            created.setRoles(Set.of(role));
            return created;
        });
        user.addRole(role);
        if (!user.hasPassword() && password != null && !password.isBlank()) {
            user.setPasswordHash(passwordHasher.hash(password));
        }
        users.save(user);
    }
}
