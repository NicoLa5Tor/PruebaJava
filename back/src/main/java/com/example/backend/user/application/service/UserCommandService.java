package com.example.backend.user.application.service;

import com.example.backend.shared.domain.exception.BusinessRuleException;
import com.example.backend.shared.domain.exception.ConflictException;
import com.example.backend.shared.domain.exception.ResourceNotFoundException;
import com.example.backend.user.application.port.in.UserCommandUseCase;
import com.example.backend.user.application.port.out.PasswordHasherPort;
import com.example.backend.user.application.port.out.UserRepositoryPort;
import com.example.backend.user.domain.model.User;
import com.example.backend.user.domain.model.UserRole;
import com.example.backend.user.domain.model.UserStatus;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserCommandService implements UserCommandUseCase {
    static final int MIN_PASSWORD_LENGTH = 8;

    private final UserRepositoryPort users;
    private final PasswordHasherPort passwordHasher;

    public UserCommandService(UserRepositoryPort users, PasswordHasherPort passwordHasher) {
        this.users = users;
        this.passwordHasher = passwordHasher;
    }

    @Override
    public User create(CreateUserCommand command) {
        String email = User.normalizeEmail(command.email());
        String logtoSubject = normalizeOptional(command.logtoSubject());
        ensureEmailAvailable(email, null);
        ensureLogtoSubjectAvailable(logtoSubject, null);

        User user = User.newUser(email);
        user.setFirstName(normalizeOptional(command.firstName()));
        user.setLastName(normalizeOptional(command.lastName()));
        user.setDisplayName(normalizeOptional(command.displayName()));
        user.setAvatarUrl(normalizeOptional(command.avatarUrl()));
        user.setLogtoSubject(logtoSubject);
        user.setRoles(command.roles());
        if (command.password() != null && !command.password().isBlank()) {
            user.setPasswordHash(hashPassword(command.password()));
        }
        return users.save(user);
    }

    @Override
    public User updateProfile(UUID userId, UpdateProfileCommand command) {
        User user = findUser(userId);
        user.setFirstName(normalizeOptional(command.firstName()));
        user.setLastName(normalizeOptional(command.lastName()));
        user.setDisplayName(normalizeOptional(command.displayName()));
        user.setAvatarUrl(normalizeOptional(command.avatarUrl()));
        return users.save(user);
    }

    @Override
    public User update(UUID id, UpdateUserCommand command, UUID actingUserId) {
        if (id.equals(actingUserId)) {
            if (command.roles() != null && !command.roles().contains(UserRole.SUPER_ADMIN)) {
                throw new BusinessRuleException("You cannot remove your own super administrator role.");
            }
            if (command.status() == UserStatus.INACTIVE) {
                throw new BusinessRuleException("You cannot deactivate your own account.");
            }
        }

        User user = findUser(id);
        if (command.email() != null) {
            String email = User.normalizeEmail(command.email());
            ensureEmailAvailable(email, id);
            user.setEmail(email);
        }
        if (command.firstName() != null) {
            user.setFirstName(normalizeOptional(command.firstName()));
        }
        if (command.lastName() != null) {
            user.setLastName(normalizeOptional(command.lastName()));
        }
        if (command.displayName() != null) {
            user.setDisplayName(normalizeOptional(command.displayName()));
        }
        if (command.avatarUrl() != null) {
            user.setAvatarUrl(normalizeOptional(command.avatarUrl()));
        }
        if (command.logtoSubject() != null) {
            String logtoSubject = normalizeOptional(command.logtoSubject());
            ensureLogtoSubjectAvailable(logtoSubject, id);
            user.setLogtoSubject(logtoSubject);
        }
        if (command.emailVerified() != null) {
            user.setEmailVerified(command.emailVerified());
        }
        if (command.status() != null) {
            user.setStatus(command.status());
        }
        if (command.roles() != null) {
            user.setRoles(command.roles());
        }
        return users.save(user);
    }

    @Override
    public void changePassword(UUID id, String newPassword) {
        User user = findUser(id);
        user.setPasswordHash(hashPassword(newPassword));
        users.save(user);
    }

    @Override
    public void delete(UUID id, UUID actingUserId) {
        if (id.equals(actingUserId)) {
            throw new BusinessRuleException("You cannot delete your own account.");
        }
        findUser(id);
        users.deleteById(id);
    }

    private String hashPassword(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new BusinessRuleException("The password must have at least %d characters.".formatted(MIN_PASSWORD_LENGTH));
        }
        return passwordHasher.hash(rawPassword);
    }

    private User findUser(UUID id) {
        return users.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User with id %s was not found.".formatted(id)));
    }

    private void ensureEmailAvailable(String email, UUID currentUserId) {
        users.findByEmail(email)
                .filter(user -> !user.getId().equals(currentUserId))
                .ifPresent(user -> {
                    throw new ConflictException("A user with this email already exists.");
                });
    }

    private void ensureLogtoSubjectAvailable(String logtoSubject, UUID currentUserId) {
        if (logtoSubject == null) {
            return;
        }
        users.findByLogtoSubject(logtoSubject)
                .filter(user -> !user.getId().equals(currentUserId))
                .ifPresent(user -> {
                    throw new ConflictException("A user with this Logto subject already exists.");
                });
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
