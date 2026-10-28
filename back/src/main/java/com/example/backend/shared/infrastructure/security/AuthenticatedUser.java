package com.example.backend.shared.infrastructure.security;

import java.util.List;
import java.util.UUID;

/** The principal the JWT filter puts in the security context; controllers read it with @AuthenticationPrincipal. */
public record AuthenticatedUser(UUID userId, String email, List<String> roles) {

    public AuthenticatedUser {
        roles = List.copyOf(roles);
    }

    public boolean hasAnyRole(String... candidates) {
        for (String candidate : candidates) {
            if (roles.contains(candidate)) {
                return true;
            }
        }
        return false;
    }
}
