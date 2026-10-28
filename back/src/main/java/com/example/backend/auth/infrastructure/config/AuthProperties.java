package com.example.backend.auth.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth")
public record AuthProperties(Jwt jwt, Logto logto, RefreshCookie refreshCookie, Turnstile turnstile) {
    public record Jwt(String secret, Duration accessTokenTtl, Duration refreshTokenTtl) {
    }

    public record Logto(String issuer, String appId) {
    }

    public record RefreshCookie(String name, String path, boolean secure) {
    }

    /** A blank secret key disables Turnstile (local development and tests). */
    public record Turnstile(String secretKey, String verifyUrl) {
        public boolean enabled() {
            return secretKey != null && !secretKey.isBlank();
        }
    }
}
