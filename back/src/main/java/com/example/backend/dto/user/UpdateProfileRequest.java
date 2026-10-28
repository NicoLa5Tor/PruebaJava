package com.example.backend.dto.user;

import jakarta.validation.constraints.Size;

/** Fields a user may change on their own account; email, roles and status stay under admin control. */
public record UpdateProfileRequest(
        @Size(max = 100) String firstName,
        @Size(max = 100) String lastName,
        @Size(max = 150) String displayName,
        @Size(max = 2048) String avatarUrl
) {
}
