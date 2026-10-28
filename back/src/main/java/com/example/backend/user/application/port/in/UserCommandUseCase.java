package com.example.backend.user.application.port.in;

import com.example.backend.user.domain.model.User;
import com.example.backend.user.domain.model.UserRole;
import com.example.backend.user.domain.model.UserStatus;
import java.util.Set;
import java.util.UUID;

public interface UserCommandUseCase {

    User create(CreateUserCommand command);

    /** A user editing their own account; email, roles and status stay under super admin control. */
    User updateProfile(UUID userId, UpdateProfileCommand command);

    /** Super admin update. {@code actingUserId} keeps the super admin from locking themselves out. */
    User update(UUID id, UpdateUserCommand command, UUID actingUserId);

    void changePassword(UUID id, String newPassword);

    void delete(UUID id, UUID actingUserId);

    record CreateUserCommand(String email, String firstName, String lastName, String displayName, String avatarUrl,
                             String logtoSubject, Set<UserRole> roles, String password) {
    }

    record UpdateProfileCommand(String firstName, String lastName, String displayName, String avatarUrl) {
    }

    /** Null fields are left unchanged; a blank string clears an optional text field. */
    record UpdateUserCommand(String email, String firstName, String lastName, String displayName, String avatarUrl,
                             String logtoSubject, Boolean emailVerified, UserStatus status, Set<UserRole> roles) {
    }
}
