package com.example.backend.auth.domain.model;

import java.time.Instant;
import java.util.UUID;

/** Only the SHA-256 hash of the token is kept; the raw value lives solely in the client's HttpOnly cookie. */
public class RefreshToken {

    private final UUID id;
    private final UUID userId;
    private final String tokenHash;
    private final Instant expiresAt;
    private Instant revokedAt;

    public RefreshToken(UUID id, UUID userId, String tokenHash, Instant expiresAt, Instant revokedAt) {
        this.id = id;
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.revokedAt = revokedAt;
    }

    public static RefreshToken issue(UUID userId, String tokenHash, Instant expiresAt) {
        return new RefreshToken(null, userId, tokenHash, expiresAt, null);
    }

    public boolean isUsableAt(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    public void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}
