package com.example.backend.notification.application.port.out;

import com.example.backend.notification.domain.model.Notification;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepositoryPort {

    Notification save(Notification notification);

    Optional<Notification> findById(UUID id);

    List<Notification> findRecentByUser(UUID userId, int limit);

    long countUnread(UUID userId);

    void markAllRead(UUID userId, Instant now);
}
