package com.example.backend.user.infrastructure.seed;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.seed")
public record SeedProperties(String superAdminEmail, String superAdminPassword, boolean demoUsers) {
}
