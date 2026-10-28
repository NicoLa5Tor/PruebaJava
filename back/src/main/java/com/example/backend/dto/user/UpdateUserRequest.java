package com.example.backend.dto.user;

import com.example.backend.model.user.UserRole;
import com.example.backend.model.user.UserStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import java.util.Set;

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
}
