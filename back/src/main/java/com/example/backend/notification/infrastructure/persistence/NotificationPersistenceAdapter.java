package com.example.backend.notification.infrastructure.persistence;

import com.example.backend.notification.application.port.out.NotificationRepositoryPort;
import com.example.backend.notification.domain.model.Notification;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
class NotificationPersistenceAdapter implements NotificationRepositoryPort {

    private final SpringDataNotificationRepository repository;

    NotificationPersistenceAdapter(SpringDataNotificationRepository repository) {
        this.repository = repository;
    }

    @Override
    public Notification save(Notification notification) {
        NotificationJpaEntity entity = notification.id() == null
                ? new NotificationJpaEntity()
                : repository.findById(notification.id()).orElseThrow();
        entity.apply(notification);
        return repository.saveAndFlush(entity).toDomain();
    }

    @Override
    public Optional<Notification> findById(UUID id) {
        return repository.findById(id).map(NotificationJpaEntity::toDomain);
    }

    @Override
    public List<Notification> findRecentByUser(UUID userId, int limit) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, limit)).stream()
                .map(NotificationJpaEntity::toDomain)
                .toList();
    }

    @Override
    public long countUnread(UUID userId) {
        return repository.countByUserIdAndReadAtIsNull(userId);
    }

    @Override
    public void markAllRead(UUID userId, Instant now) {
        repository.markAllRead(userId, now);
    }
}
