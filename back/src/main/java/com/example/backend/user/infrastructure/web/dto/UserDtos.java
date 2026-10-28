package com.example.backend.user.infrastructure.web.dto;

import com.example.backend.user.application.port.in.UserCommandUseCase.CreateUserCommand;
import com.example.backend.user.application.port.in.UserCommandUseCase.UpdateProfileCommand;
import com.example.backend.user.application.port.in.UserCommandUseCase.UpdateUserCommand;
import com.example.backend.user.domain.model.User;
import com.example.backend.user.domain.model.UserRole;
import com.example.backend.user.domain.model.UserStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** HTTP contract of /api/v1/users. Requests become use case commands; responses never expose the password hash. */
public final class UserDtos {

    private UserDtos() {
    }

    public record CreateUserRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @Size(max = 100) String firstName,
            @Size(max = 100) String lastName,
            @Size(max = 150) String displayName,
            @Size(max = 2048) String avatarUrl,
            @Size(max = 255) String logtoSubject,
            Set<UserRole> roles,
            @Size(min = 8, max = 72) String password
    ) {
        public CreateUserCommand toCommand() {
            return new CreateUserCommand(email, firstName, lastName, displayName, avatarUrl, logtoSubject, roles, password);
        }
    }

    public record UpdateUserRequest(
            @Email @Size(max = 254) String email,
            @Size(max = 100) String firstName,
            @Size(max = 100) String lastName,
            @Size(max = 150) String displayName,
            @Size(max = 2048) String avatarUrl,
            @Size(max = 255) String logtoSubject,
            Boolean emailVerified,
            UserStatus status,
            Set<UserRole> roles
    ) {
        public UpdateUserCommand toCommand() {
            return new UpdateUserCommand(email, firstName, lastName, displayName, avatarUrl, logtoSubject, emailVerified,
                    status, roles);
        }
    }

    /** Fields a user may change on their own account; email, roles and status stay under super admin control. */
    public record UpdateProfileRequest(
            @Size(max = 100) String firstName,
            @Size(max = 100) String lastName,
            @Size(max = 150) String displayName,
            @Size(max = 2048) String avatarUrl
    ) {
        public UpdateProfileCommand toCommand() {
            return new UpdateProfileCommand(firstName, lastName, displayName, avatarUrl);
        }
    }

    public record ChangePasswordRequest(@NotBlank @Size(min = 8, max = 72) String password) {
    }

    public record UserResponse(
            UUID id,
            String email,
            String firstName,
            String lastName,
            String displayName,
            String avatarUrl,
            String logtoSubject,
            boolean emailVerified,
            boolean hasPassword,
            UserStatus status,
            Set<UserRole> roles,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static UserResponse from(User user) {
            return new UserResponse(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(),
                    user.getDisplayName(), user.getAvatarUrl(), user.getLogtoSubject(), user.isEmailVerified(),
                    user.hasPassword(), user.getStatus(), user.getRoles(), user.getCreatedAt(), user.getUpdatedAt());
        }
    }
}
