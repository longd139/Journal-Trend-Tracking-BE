package com.sra.journal_tracking.service;

import com.sra.journal_tracking.entity.jpa.Follow;
import com.sra.journal_tracking.entity.jpa.Notification;
import com.sra.journal_tracking.entity.jpa.NotificationType;
import com.sra.journal_tracking.entity.jpa.PaperKeyword;
import com.sra.journal_tracking.entity.jpa.ResearchPaper;
import com.sra.journal_tracking.repository.jpa.FollowRepository;
import com.sra.journal_tracking.repository.jpa.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Service tạo notification khi có bài báo mới được sync vào hệ thống.
 * Được gọi từ DataSyncServiceImpl sau khi lưu paper thành công.
 *
 * Flow: Paper mới → tìm user đang follow journal/keyword của paper đó →
 *       tạo notification "new_paper" cho từng user.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationTriggerService {

    private final FollowRepository followRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationEventPublisher eventPublisher;

    /**
     * Tạo notification cho tất cả user follow journal hoặc keyword của paper này.
     * Chạy bất đồng bộ để không block data sync pipeline.
     */
    @Async
    @Transactional
    public void notifyNewPaper(ResearchPaper paper) {
        if (paper == null) return;

        try {
            Set<UUID> notifiedUserIds = new HashSet<>();
            List<Notification> notifications = new ArrayList<>();

            // ── 1. Journal followers ──
            if (paper.getJournal() != null) {
                List<Follow> journalFollowers = followRepository
                        .findByJournal_JournalIdAndNotifyEnabledTrue(paper.getJournal().getJournalId());
                for (Follow follow : journalFollowers) {
                    if (notifiedUserIds.add(follow.getUser().getUserId())) {
                        notifications.add(buildNotification(follow, paper));
                    }
                }
                if (!journalFollowers.isEmpty()) {
                    log.debug("Found {} journal followers for paper '{}' (journal: {})",
                            journalFollowers.size(), paper.getTitle(), paper.getJournal().getJournalName());
                }
            }

            // ── 2. Keyword followers (batch query — avoids N+1) ──
            if (paper.getKeywords() != null && !paper.getKeywords().isEmpty()) {
                List<UUID> keywordIds = paper.getKeywords().stream()
                        .map(pk -> pk.getKeyword() != null ? pk.getKeyword().getKeywordId() : null)
                        .filter(java.util.Objects::nonNull)
                        .distinct()
                        .toList();
                if (!keywordIds.isEmpty()) {
                    List<Follow> keywordFollowers = followRepository
                            .findByKeyword_KeywordIdInAndNotifyEnabledTrue(keywordIds);
                    for (Follow follow : keywordFollowers) {
                        if (notifiedUserIds.add(follow.getUser().getUserId())) {
                            notifications.add(buildNotification(follow, paper));
                        }
                    }
                    if (!keywordFollowers.isEmpty()) {
                        log.debug("Found {} keyword followers for paper '{}' ({} keywords queried)",
                                keywordFollowers.size(), paper.getTitle(), keywordIds.size());
                    }
                }
            }

            // ── 3. Topic / Research Field followers ──
            if (paper.getField() != null) {
                List<Follow> topicFollowers = followRepository
                        .findByTopic_TopicIdAndNotifyEnabledTrue(paper.getField().getFieldId());
                for (Follow follow : topicFollowers) {
                    if (notifiedUserIds.add(follow.getUser().getUserId())) {
                        notifications.add(buildNotification(follow, paper));
                    }
                }
                if (!topicFollowers.isEmpty()) {
                    log.debug("Found {} topic followers for paper '{}' (field: {})",
                            topicFollowers.size(), paper.getTitle(), paper.getField().getFieldName());
                }
            }

            // ── 4. Batch save ──
            if (!notifications.isEmpty()) {
                notificationRepository.saveAll(notifications);
                log.info("Created {} notifications for new paper: '{}' ({} unique users)",
                        notifications.size(), paper.getTitle(), notifiedUserIds.size());

                // Push to connected SSE clients
                for (Notification n : notifications) {
                    try {
                        eventPublisher.publish(n.getUser().getUserId(), n);
                    } catch (Exception ignored) { /* best-effort */ }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to create notifications for paper '{}': {}", paper.getTitle(), e.getMessage());
        }
    }

    private Notification buildNotification(Follow follow, ResearchPaper paper) {
        String journalName = paper.getJournal() != null ? paper.getJournal().getJournalName() : null;
        String title = journalName != null
                ? "New paper in " + journalName
                : "New paper matching your interests";

        String message = "\"" + paper.getTitle() + "\""
                + (paper.getPubYear() != null ? " (" + paper.getPubYear() + ")" : "")
                + (journalName != null ? " — published in " + journalName : "");

        return Notification.builder()
                .user(follow.getUser())
                .type(NotificationType.NEW_PAPER)
                .title(title)
                .message(message)
                .relatedPaper(paper)
                .relatedJournal(paper.getJournal())
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
