package com.example.backend.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** No email, role or status here: the email comes from the verified Logto token and new accounts are always USER. */
public record RegisterRequest(
        @NotBlank String idToken,
        @NotBlank @Size(max = 100) String firstName,
        @Size(max = 100) String lastName,
        @Size(max = 150) String displayName
) {
}
