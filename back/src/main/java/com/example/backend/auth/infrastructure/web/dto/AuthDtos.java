package com.example.backend.auth.infrastructure.web.dto;

import com.example.backend.auth.application.port.in.AuthenticationUseCase.RegisterCommand;
import com.example.backend.auth.domain.model.AccessToken;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** HTTP contract of /api/v1/auth. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record PasswordLoginRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 72) String password,
            /** Cloudflare Turnstile token; required only when Turnstile is enabled. */
            @Size(max = 2048) String turnstileToken
    ) {
    }

    public record LogtoSignInRequest(@NotBlank String idToken) {
    }

    /** No email, role or status here: the email comes from the verified Logto token and new accounts are always USER. */
    public record RegisterRequest(
            @NotBlank String idToken,
            @NotBlank @Size(max = 100) String firstName,
            @Size(max = 100) String lastName,
            @Size(max = 150) String displayName
    ) {
        public RegisterCommand toCommand() {
            return new RegisterCommand(idToken, firstName, lastName, displayName);
        }
    }

    public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
        public static TokenResponse from(AccessToken token) {
            return new TokenResponse(token.value(), "Bearer", token.expiresInSeconds());
        }
    }
}
