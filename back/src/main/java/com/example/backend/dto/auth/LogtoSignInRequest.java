package com.example.backend.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record LogtoSignInRequest(@NotBlank String idToken) {
}
