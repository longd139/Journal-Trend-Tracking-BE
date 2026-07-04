package com.sra.journal_tracking.service;

import com.sra.journal_tracking.controller.NotificationSseController;
import com.sra.journal_tracking.service.NotificationEventPublisher.NotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Listens for {@link NotificationEvent} published by {@link NotificationEventPublisher}
 * and fans them out to the correct user's SSE connections via {@link NotificationSseController}.
 * <p>
 * Runs asynchronously so event publishing never blocks the notification creation thread.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationSseController sseController;

    @EventListener
    @Async
    public void handleNotificationEvent(NotificationEvent event) {
        log.debug("SSE push: notif {} for user {}", event.notifId(), event.userId());
        sseController.sendToUser(event.userId(), event);
    }
}
