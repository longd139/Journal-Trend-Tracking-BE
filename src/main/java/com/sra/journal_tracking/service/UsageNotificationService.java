package com.sra.journal_tracking.service;

import com.sra.journal_tracking.entity.jpa.Notification;
import com.sra.journal_tracking.entity.jpa.NotificationType;
import com.sra.journal_tracking.entity.jpa.User;
import com.sra.journal_tracking.repository.jpa.NotificationRepository;
import com.sra.journal_tracking.repository.jpa.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.UUID;

/**
 * Handles UPGRADE_PROMPT notification creation in a NEW transaction
 * so it survives rollback in the calling service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UsageNotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final NotificationEventPublisher eventPublisher;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createUpgradePromptNotification(UUID userId, String usageType, int currentCount, int limit, boolean limitReached) {
        try {
            LocalDateTime monthStart = YearMonth.now().atDay(1).atStartOfDay();
            long existing = notificationRepository.countByUserAndTypeSince(
                    userId, NotificationType.UPGRADE_PROMPT, monthStart);
            if (existing > 0) {
                log.debug("UPGRADE_PROMPT already exists for user {} this month — skipping", userId);
                return;
            }

            User user = userRepository.findById(userId).orElse(null);
            if (user == null) return;

            String title;
            String message;
            if (limitReached) {
                title = "Usage limit reached";
                message = String.format(
                        "You have used all %d %ss this month. Upgrade to Researcher for unlimited access.",
                        limit, usageType);
            } else {
                title = "Usage limit warning";
                message = String.format(
                        "You have used %d of %d %ss this month (80%%). Consider upgrading to Researcher for unlimited access.",
                        currentCount, limit, usageType);
            }

            Notification notification = notificationRepository.save(Notification.builder()
                    .user(user)
                    .type(NotificationType.UPGRADE_PROMPT)
                    .title(title)
                    .message(message)
                    .isRead(false)
                    .createdAt(LocalDateTime.now())
                    .build());

            log.info("Created UPGRADE_PROMPT notification {} for user {}", notification.getNotifId(), userId);

            try {
                eventPublisher.publish(userId, notification);
            } catch (Exception ignored) { /* best-effort */ }
        } catch (Exception e) {
            log.warn("Failed to create UPGRADE_PROMPT notification for user {}: {}", userId, e.getMessage());
        }
    }
}
