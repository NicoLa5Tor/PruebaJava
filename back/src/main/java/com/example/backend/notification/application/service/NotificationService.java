package com.example.backend.notification.application.service;

import com.example.backend.notification.application.port.in.NotificationUseCase;
import com.example.backend.notification.application.port.out.NotificationRepositoryPort;
import com.example.backend.notification.domain.model.Notification;
import com.example.backend.notification.domain.model.NotificationType;
import com.example.backend.shared.domain.exception.ResourceNotFoundException;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class NotificationService implements NotificationUseCase {

    private final NotificationRepositoryPort notifications;
    private final Clock clock;

    public NotificationService(NotificationRepositoryPort notifications, Clock clock) {
        this.notifications = notifications;
        this.clock = clock;
    }

    @Override
    public Notification send(UUID userId, UUID loanId, NotificationType type, String title, String message) {
        return notifications.save(Notification.create(userId, loanId, type, title, message));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Notification> recent(UUID userId) {
        return notifications.findRecentByUser(userId, MAX_LISTED);
    }

    @Override
    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return notifications.countUnread(userId);
    }

    @Override
    public Notification markRead(UUID notificationId, UUID userId) {
        Notification notification = notifications.findById(notificationId)
                .filter(found -> found.userId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Notification with id %s was not found.".formatted(notificationId)));
        return notification.isRead() ? notification : notifications.save(notification.markRead(clock.instant()));
    }

    @Override
    public void markAllRead(UUID userId) {
        notifications.markAllRead(userId, clock.instant());
    }
}
