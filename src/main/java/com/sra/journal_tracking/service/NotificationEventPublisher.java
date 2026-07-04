package com.sra.journal_tracking.service;

import com.sra.journal_tracking.entity.jpa.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Publishes {@link NotificationEvent} to the Spring application context
 * whenever a new notification is persisted, so SSE listeners can push
 * it to connected clients in real time.
 *
 * @see NotificationEventListener
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationEventPublisher {

    private final ApplicationEventPublisher publisher;

    /**
     * Fire an event for a newly created notification.
     *
     * @param userId       the target user ID
     * @param notification the saved notification entity
     */
    public void publish(UUID userId, Notification notification) {
        NotificationEvent event = new NotificationEvent(
                userId,
                notification.getNotifId(),
                notification.getType().name().toLowerCase(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getRelatedPaper() != null
                        ? notification.getRelatedPaper().getPaperId() : null,
                notification.getRelatedJournal() != null
                        ? notification.getRelatedJournal().getJournalName() : null,
                notification.getCreatedAt()
        );
        publisher.publishEvent(event);
        log.debug("Published NotificationEvent for user {}: {}", userId, notification.getNotifId());
    }

    /**
     * Lightweight event payload carrying the fields the frontend needs
     * to render a notification card without an extra API call.
     */
    public record NotificationEvent(
            UUID userId,
            UUID notifId,
            String type,
            String title,
            String message,
            UUID relatedPaperId,
            String relatedJournalName,
            LocalDateTime createdAt
    ) {}
}
