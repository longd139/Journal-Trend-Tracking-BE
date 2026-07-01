package com.sra.journal_tracking.service.impl;

import com.sra.journal_tracking.dto.notification.NotificationResponse;
import com.sra.journal_tracking.dto.notification.UnreadCountResponse;
import com.sra.journal_tracking.entity.jpa.Notification;
import com.sra.journal_tracking.entity.jpa.User;
import com.sra.journal_tracking.exception.AppException;
import com.sra.journal_tracking.exception.ErrorCode;
import com.sra.journal_tracking.repository.jpa.NotificationRepository;
import com.sra.journal_tracking.repository.jpa.UserRepository;
import com.sra.journal_tracking.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public List<NotificationResponse> getNotifications(String email, int page, int size, String filter) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        PageRequest pageRequest = PageRequest.of(page, size);

        Page<Notification> result;
        if ("unread".equalsIgnoreCase(filter)) {
            result = notificationRepository.findByUser_UserIdAndIsReadFalseOrderByCreatedAtDesc(
                    user.getUserId(), pageRequest);
        } else {
            result = notificationRepository.findByUser_UserIdOrderByCreatedAtDesc(
                    user.getUserId(), pageRequest);
        }

        return result.getContent().stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public UnreadCountResponse getUnreadCount(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        long count = notificationRepository.countByUser_UserIdAndIsReadFalse(user.getUserId());
        return new UnreadCountResponse(count);
    }

    @Override
    @Transactional
    public void markAsRead(String email, UUID notifId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Notification notification = notificationRepository.findById(notifId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));

        // Verify ownership
        if (!notification.getUser().getUserId().equals(user.getUserId())) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        if (Boolean.FALSE.equals(notification.getIsRead())) {
            notification.setIsRead(true);
            notificationRepository.save(notification);
            log.debug("Notification {} marked as read for user {}", notifId, email);
        }
    }

    @Override
    @Transactional
    public void markAllAsRead(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        int updated = notificationRepository.markAllAsRead(user.getUserId());
        log.info("Marked {} notifications as read for user {}", updated, email);
    }

    @Override
    @Transactional
    public void deleteNotification(String email, UUID notifId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Notification notification = notificationRepository.findById(notifId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));

        // Verify ownership
        if (!notification.getUser().getUserId().equals(user.getUserId())) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        notificationRepository.delete(notification);
        log.debug("Notification {} deleted for user {}", notifId, email);
    }

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
