package com.example.backend.dto.user;

import com.example.backend.model.user.UserRole;
import com.example.backend.model.user.UserStatus;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        String displayName,
        String avatarUrl,
        String logtoSubject,
        boolean emailVerified,
        UserStatus status,
        Set<UserRole> roles,
        Instant createdAt,
        Instant updatedAt
) {
}
