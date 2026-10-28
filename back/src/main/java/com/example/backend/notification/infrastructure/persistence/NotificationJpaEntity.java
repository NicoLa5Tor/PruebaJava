package com.example.backend.notification.infrastructure.persistence;

import com.example.backend.notification.domain.model.Notification;
import com.example.backend.notification.domain.model.NotificationType;
import com.example.backend.shared.infrastructure.persistence.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity(name = "Notification")
@Table(name = "notifications")
class NotificationJpaEntity extends BaseJpaEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "loan_id", updatable = false)
    private UUID loanId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private NotificationType type;

    @Column(nullable = false, length = 150, updatable = false)
    private String title;

    @Column(nullable = false, length = 1000, updatable = false)
    private String message;

    private Instant readAt;

    void apply(Notification notification) {
        userId = notification.userId();
        loanId = notification.loanId();
        type = notification.type();
        title = notification.title();
        message = notification.message();
        readAt = notification.readAt();
    }

    Notification toDomain() {
        return Notification.restore(getId(), userId, loanId, type, title, message, readAt, getCreatedAt());
    }
}
