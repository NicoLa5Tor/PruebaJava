package com.example.backend.notification.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A message for one user, shown in the app's inbox until it is read. */
public final class Notification {
    public static final int MAX_TITLE_LENGTH = 150;
    public static final int MAX_MESSAGE_LENGTH = 1000;

    private final UUID id;
    private final UUID userId;
    private final UUID loanId;
    private final NotificationType type;
    private final String title;
    private final String message;
    private final Instant readAt;
    private final Instant createdAt;

    private Notification(UUID id, UUID userId, UUID loanId, NotificationType type, String title, String message,
                         Instant readAt, Instant createdAt) {
        this.id = id;
        this.userId = Objects.requireNonNull(userId, "userId");
        this.loanId = loanId;
        this.type = Objects.requireNonNull(type, "type");
        this.title = truncate(Objects.requireNonNull(title, "title"), MAX_TITLE_LENGTH);
        this.message = truncate(Objects.requireNonNull(message, "message"), MAX_MESSAGE_LENGTH);
        this.readAt = readAt;
        this.createdAt = createdAt;
    }

    public static Notification create(UUID userId, UUID loanId, NotificationType type, String title, String message) {
        return new Notification(null, userId, loanId, type, title, message, null, null);
    }

    public static Notification restore(UUID id, UUID userId, UUID loanId, NotificationType type, String title,
                                       String message, Instant readAt, Instant createdAt) {
        return new Notification(id, userId, loanId, type, title, message, readAt, createdAt);
    }

    public Notification markRead(Instant now) {
        return readAt != null ? this : new Notification(id, userId, loanId, type, title, message, now, createdAt);
    }

    public boolean isRead() {
        return readAt != null;
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }

    public UUID id() { return id; }
    public UUID userId() { return userId; }
    public UUID loanId() { return loanId; }
    public NotificationType type() { return type; }
    public String title() { return title; }
    public String message() { return message; }
    public Instant readAt() { return readAt; }
    public Instant createdAt() { return createdAt; }
}
