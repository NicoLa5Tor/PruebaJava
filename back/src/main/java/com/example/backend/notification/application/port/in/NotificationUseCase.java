package com.example.backend.notification.application.port.in;

import com.example.backend.notification.domain.model.Notification;
import com.example.backend.notification.domain.model.NotificationType;
import java.util.List;
import java.util.UUID;

public interface NotificationUseCase {
    int MAX_LISTED = 50;

    Notification send(UUID userId, UUID loanId, NotificationType type, String title, String message);

    /** Newest first, at most {@link #MAX_LISTED}. */
    List<Notification> recent(UUID userId);

    long unreadCount(UUID userId);

    /** Another user's notification is reported as missing. */
    Notification markRead(UUID notificationId, UUID userId);

    void markAllRead(UUID userId);
}
