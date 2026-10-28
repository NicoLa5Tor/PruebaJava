package com.example.backend.notification.infrastructure.web;

import com.example.backend.notification.application.port.in.NotificationUseCase;
import com.example.backend.notification.domain.model.Notification;
import com.example.backend.notification.domain.model.NotificationType;
import com.example.backend.shared.infrastructure.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in user's inbox. */
@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications")
@PreAuthorize("isAuthenticated()")
public class NotificationController {

    private final NotificationUseCase notifications;

    public NotificationController(NotificationUseCase notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    @Operation(summary = "Latest notifications of the caller, newest first")
    public List<NotificationResponse> recent(@AuthenticationPrincipal AuthenticatedUser principal) {
        return notifications.recent(principal.userId()).stream().map(NotificationResponse::from).toList();
    }

    @GetMapping("/unread-count")
    @Operation(summary = "How many notifications the caller has not read")
    public UnreadCountResponse unreadCount(@AuthenticationPrincipal AuthenticatedUser principal) {
        return new UnreadCountResponse(notifications.unreadCount(principal.userId()));
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "Mark one notification as read")
    public NotificationResponse markRead(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
        return NotificationResponse.from(notifications.markRead(id, principal.userId()));
    }

    @PatchMapping("/read-all")
    @Operation(summary = "Mark every notification of the caller as read")
    public ResponseEntity<Void> markAllRead(@AuthenticationPrincipal AuthenticatedUser principal) {
        notifications.markAllRead(principal.userId());
        return ResponseEntity.noContent().build();
    }

    public record NotificationResponse(UUID id, UUID loanId, NotificationType type, String title, String message,
                                       boolean read, Instant readAt, Instant createdAt) {
        static NotificationResponse from(Notification notification) {
            return new NotificationResponse(notification.id(), notification.loanId(), notification.type(),
                    notification.title(), notification.message(), notification.isRead(), notification.readAt(),
                    notification.createdAt());
        }
    }

    public record UnreadCountResponse(long unread) {
    }
}
