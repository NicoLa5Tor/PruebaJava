package com.example.backend.auth.domain.model;

public record AccessToken(String value, long expiresInSeconds) {
}
