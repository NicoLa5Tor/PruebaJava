package com.example.backend.dto.auth;

public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
}
