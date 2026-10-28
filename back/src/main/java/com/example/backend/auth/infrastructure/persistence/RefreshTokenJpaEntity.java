package com.example.backend.auth.infrastructure.persistence;

import com.example.backend.auth.domain.model.RefreshToken;
import com.example.backend.shared.infrastructure.persistence.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Refers to its user by id only; the database cascades the delete when the user goes away. */
@Entity(name = "RefreshToken")
@Table(name = "refresh_tokens")
class RefreshTokenJpaEntity extends BaseJpaEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, unique = true, length = 64, updatable = false)
    private String tokenHash;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant revokedAt;

    void apply(RefreshToken token) {
        userId = token.getUserId();
        tokenHash = token.getTokenHash();
        expiresAt = token.getExpiresAt();
        revokedAt = token.getRevokedAt();
    }

    RefreshToken toDomain() {
        return new RefreshToken(getId(), userId, tokenHash, expiresAt, revokedAt);
    }
}
