package com.sra.journal_tracking.service.impl;

import com.sra.journal_tracking.dto.notification.NotificationResponse;
import com.sra.journal_tracking.entity.jpa.Notification;
import com.sra.journal_tracking.entity.jpa.NotificationType;
import com.sra.journal_tracking.entity.jpa.User;
import com.sra.journal_tracking.exception.AppException;
import com.sra.journal_tracking.exception.ErrorCode;
import com.sra.journal_tracking.repository.jpa.NotificationRepository;
import com.sra.journal_tracking.repository.jpa.UserRepository;
import com.sra.journal_tracking.service.AdminNotificationService;
import com.sra.journal_tracking.service.EmailService;
import com.sra.journal_tracking.service.NotificationEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Implementation của AdminNotificationService.
 * <p>
 * Broadcast pattern: Khi có sự kiện admin (new user, sync done, report, ...),
 * tạo 1 bản ghi Notification cho mỗi admin đang active. Dùng chung bảng
 * NOTIFICATION hiện có — mỗi admin có row riêng → trạng thái isRead độc lập.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminNotificationServiceImpl implements AdminNotificationService {

    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationEventPublisher eventPublisher;
    private final EmailService emailService;

    /**
     * Broadcast notification tới tất cả admin đang active.
     * Tạo 1 row Notification cho mỗi admin, publish SSE event real-time.
     */
    @Override
    @Transactional
    public void broadcastToAdmins(NotificationType type, String title, String message) {
        List<User> admins = userRepository.findByRole_RoleName("admin");

        if (admins.isEmpty()) {
            log.warn("No admin users found — skipping broadcast for type={} title=\"{}\"", type, title);
            return;
        }

        log.info("Broadcasting notification type={} title=\"{}\" to {} admin(s)", type, title, admins.size());

        for (User admin : admins) {
            try {
                Notification notification = Notification.builder()
                        .user(admin)
                        .type(type)
                        .title(title)
                        .message(message)
                        .isRead(false)
                        .build();

                notification = notificationRepository.save(notification);

                // Push SSE real-time
                eventPublisher.publish(admin.getUserId(), notification);
                log.debug("Notification {} pushed to admin {}", notification.getNotifId(), admin.getEmail());

                // Send email (best-effort, async — failure won't affect in-app notification)
                try {
                    emailService.sendAdminNotification(admin.getEmail(), title, message);
                } catch (Exception emailEx) {
                    log.warn("Failed to queue admin email for {}: {}", admin.getEmail(), emailEx.getMessage());
                }

            } catch (Exception e) {
                // Best-effort: không để 1 admin fail làm hỏng toàn bộ broadcast
                log.error("Failed to create/push notification for admin {}: {}", admin.getEmail(), e.getMessage(), e);
            }
        }
    }

    /**
     * Lấy danh sách notification của admin, hỗ trợ lọc theo type.
     */
    @Override
    @Transactional(readOnly = true)
    public List<NotificationResponse> getAdminNotifications(String email, int page, int size, String typeFilter) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        PageRequest pageRequest = PageRequest.of(page, size);

        Page<Notification> result;
        if (typeFilter != null && !typeFilter.isBlank()) {
            try {
                NotificationType type = NotificationType.valueOf(typeFilter.toUpperCase());
                result = notificationRepository.findByUser_UserIdAndTypeOrderByCreatedAtDesc(
                        user.getUserId(), type, pageRequest);
            } catch (IllegalArgumentException e) {
                // Invalid type string → return empty
                log.warn("Invalid notification type filter: {}", typeFilter);
                return List.of();
            }
        } else {
            result = notificationRepository.findByUser_UserIdOrderByCreatedAtDesc(
                    user.getUserId(), pageRequest);
        }

        return result.getContent().stream()
                .map(this::mapToResponse)
                .toList();
    }

    // ── Private mapper ──

    private NotificationResponse mapToResponse(Notification n) {
        return NotificationResponse.builder()
                .notifId(n.getNotifId())
                .type(n.getType() != null ? n.getType().name().toLowerCase() : null)
                .title(n.getTitle())
                .message(n.getMessage())
                .relatedPaperId(n.getRelatedPaper() != null ? n.getRelatedPaper().getPaperId() : null)
                .relatedPaperTitle(n.getRelatedPaper() != null ? n.getRelatedPaper().getTitle() : null)
                .relatedJournalId(n.getRelatedJournal() != null ? n.getRelatedJournal().getJournalId() : null)
                .relatedJournalName(n.getRelatedJournal() != null ? n.getRelatedJournal().getJournalName() : null)
                .relatedTopicId(n.getRelatedTopic() != null ? n.getRelatedTopic().getTopicId() : null)
                .relatedTopicName(n.getRelatedTopic() != null ? n.getRelatedTopic().getTopicName() : null)
                .relatedKeywordId(n.getRelatedKeyword() != null ? n.getRelatedKeyword().getKeywordId() : null)
                .relatedKeywordText(n.getRelatedKeyword() != null ? n.getRelatedKeyword().getKeywordText() : null)
                .isRead(n.getIsRead())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
