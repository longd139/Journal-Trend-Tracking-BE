package com.sra.journal_tracking.service.impl;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sra.journal_tracking.dto.follow.FollowRequest;
import com.sra.journal_tracking.dto.follow.FollowResponse;
import com.sra.journal_tracking.dto.follow.QuickInsightResponse;
import com.sra.journal_tracking.dto.follow.WatchlistItemResponse;
import com.sra.journal_tracking.dto.journal.JournalQuickStatsResponse;
import com.sra.journal_tracking.dto.report.KeywordTrendReportResponse;
import com.sra.journal_tracking.entity.jpa.Follow;
import com.sra.journal_tracking.entity.jpa.User;
import com.sra.journal_tracking.exception.AppException;
import com.sra.journal_tracking.exception.ErrorCode;
import com.sra.journal_tracking.repository.jpa.FollowRepository;
import com.sra.journal_tracking.repository.jpa.JournalRepository;
import com.sra.journal_tracking.repository.jpa.KeywordRepository;
import com.sra.journal_tracking.repository.jpa.ResearchPaperRepository;
import com.sra.journal_tracking.repository.jpa.ResearchTopicRepository;
import com.sra.journal_tracking.repository.jpa.SystemConfigRepository;
import com.sra.journal_tracking.repository.jpa.UserRepository;
import com.sra.journal_tracking.service.BookmarkService;
import com.sra.journal_tracking.service.FollowService;
import com.sra.journal_tracking.service.JournalQuickStatsService;
import com.sra.journal_tracking.service.ReportService;

import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FollowServiceImpl implements FollowService {

    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    private final JournalRepository journalRepository;
    private final ResearchTopicRepository researchTopicRepository;
    private final KeywordRepository keywordRepository;
    private final SystemConfigRepository systemConfigRepository;
    private final ResearchPaperRepository researchPaperRepository;
    private final ReportService reportService;
    private final JournalQuickStatsService journalQuickStatsService;

    @Override
    @Transactional
    public FollowResponse addFollow(String email, FollowRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        // Validate XOR: exactly one target must be set
        int targetCount = 0;
        if (request.getJournalId() != null) targetCount++;
        if (request.getTopicId() != null) targetCount++;
        if (request.getKeywordId() != null) targetCount++;
        if (targetCount != 1) {
            throw new AppException(ErrorCode.FOLLOW_INVALID_TARGET);
        }

        // Validate target exists and check duplicate
        if (request.getJournalId() != null) {
            if (!journalRepository.existsById(request.getJournalId())) {
                throw new AppException(ErrorCode.RESOURCE_NOT_FOUND);
            }
            if (followRepository.findByUser_UserIdAndJournal_JournalId(user.getUserId(), request.getJournalId()).isPresent()) {
                throw new AppException(ErrorCode.FOLLOW_ALREADY_EXISTS);
            }
        } else if (request.getTopicId() != null) {
            if (!researchTopicRepository.existsById(request.getTopicId())) {
                throw new AppException(ErrorCode.TOPIC_NOT_FOUND);
            }
            if (followRepository.findByUser_UserIdAndTopic_TopicId(user.getUserId(), request.getTopicId()).isPresent()) {
                throw new AppException(ErrorCode.FOLLOW_ALREADY_EXISTS);
            }
        } else {
            if (!keywordRepository.existsById(request.getKeywordId())) {
                throw new AppException(ErrorCode.RESOURCE_NOT_FOUND);
            }
            if (followRepository.findByUser_UserIdAndKeyword_KeywordId(user.getUserId(), request.getKeywordId()).isPresent()) {
                throw new AppException(ErrorCode.FOLLOW_ALREADY_EXISTS);
            }
        }

        // Check limit for Academic Users
        checkFollowLimit(user);

        Follow follow = Follow.builder()
                .user(user)
                .journal(request.getJournalId() != null ? journalRepository.getReferenceById(request.getJournalId()) : null)
                .topic(request.getTopicId() != null ? researchTopicRepository.getReferenceById(request.getTopicId()) : null)
                .keyword(request.getKeywordId() != null ? keywordRepository.getReferenceById(request.getKeywordId()) : null)
                .notifyEnabled(request.getNotifyEnabled() != null ? request.getNotifyEnabled() : true)
                .build();

        follow = followRepository.save(follow);
        return mapToResponse(follow);
    }

    @Override
    public List<FollowResponse> getMyFollows(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        return followRepository.findByUser_UserId(user.getUserId())
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<WatchlistItemResponse> getMyWatchlist(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
        List<Follow> follows = followRepository.findByUser_UserId(user.getUserId());

        return follows.stream()
                .map(follow -> buildWatchlistItem(follow, weekAgo))
                .sorted(Comparator.comparing(WatchlistItemResponse::getNewPapersThisWeek,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public QuickInsightResponse getQuickInsight(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        List<Follow> follows = followRepository.findByUser_UserId(user.getUserId());
        if (follows.isEmpty()) {
            return QuickInsightResponse.builder()
                    .insight("Bạn chưa theo dõi chủ đề hay tạp chí nào. "
                            + "Hãy khám phá và theo dõi các chủ đề, tạp chí yêu thích "
                            + "để nhận báo cáo phân tích cá nhân hóa mỗi tuần.")
                    .build();
        }

        StringBuilder insight = new StringBuilder();
        insight.append("📊 Báo cáo nhanh: ");

        // Analyze followed keywords (up to 3)
        List<Follow> keywordFollows = follows.stream()
                .filter(f -> f.getKeyword() != null)
                .limit(3)
                .toList();

        int analyzed = 0;
        for (Follow f : keywordFollows) {
            try {
                KeywordTrendReportResponse trend = reportService.getKeywordTrendReport(
                        f.getKeyword().getNormalizedText());
                if (trend.getYoyGrowthRate() != null && trend.getTotalPapers() > 0) {
                    if (analyzed == 0) {
                        insight.append("Chủ đề \"")
                                .append(trend.getKeyword())
                                .append("\" bạn theo dõi đang ");
                    } else {
                        insight.append("Chủ đề \"")
                                .append(trend.getKeyword())
                                .append("\" ");
                    }

                    if (trend.getYoyGrowthRate() > 0) {
                        insight.append("có mức tăng trưởng +")
                                .append(String.format("%.1f", trend.getYoyGrowthRate()))
                                .append("% (")
                                .append(trend.getStatus().toLowerCase())
                                .append("). ");
                    } else if (trend.getYoyGrowthRate() < 0) {
                        insight.append("có mức giảm ")
                                .append(String.format("%.1f", Math.abs(trend.getYoyGrowthRate())))
                                .append("% (")
                                .append(trend.getStatus().toLowerCase())
                                .append("). ");
                    } else {
                        insight.append("đang ổn định. ");
                    }
                    analyzed++;
                }
            } catch (Exception e) {
                // Skip keywords that fail to analyze
            }
        }

        // Analyze followed journals (up to 2)
        List<Follow> journalFollows = follows.stream()
                .filter(f -> f.getJournal() != null)
                .limit(2)
                .toList();

        for (Follow f : journalFollows) {
            try {
                JournalQuickStatsResponse stats = journalQuickStatsService.getStats(
                        f.getJournal().getJournalName());
                if (stats.getQuartile() != null) {
                    insight.append("Tạp chí \"")
                            .append(stats.getJournalName())
                            .append("\" hiện đang ở nhóm ")
                            .append(stats.getQuartile());
                    if (stats.getImpactFactor() != null) {
                        insight.append(" (IF=")
                                .append(stats.getImpactFactor())
                                .append(")");
                    }
                    insight.append(". ");
                }
            } catch (Exception e) {
                // Skip journals that fail to analyze
            }
        }

        if (analyzed == 0 && journalFollows.isEmpty()) {
            insight.append("Dữ liệu về các mục bạn theo dõi đang được cập nhật. "
                    + "Hãy quay lại sau ít phút để xem báo cáo phân tích.");
        } else {
            insight.append("Hãy tiếp tục theo dõi để cập nhật xu hướng mới nhất!");
        }

        return QuickInsightResponse.builder()
                .insight(insight.toString())
                .build();
    }

    @Override
    @Transactional
    public FollowResponse updateFollow(String email, UUID followId, boolean notifyEnabled) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Follow follow = followRepository.findById(followId)
                .orElseThrow(() -> new AppException(ErrorCode.FOLLOW_NOT_FOUND));

        // Verify ownership
        if (!follow.getUser().getUserId().equals(user.getUserId())) {
            throw new AppException(ErrorCode.FOLLOW_NOT_FOUND);
        }

        follow.setNotifyEnabled(notifyEnabled);
        follow = followRepository.save(follow);
        return mapToResponse(follow);
    }

    @Override
    @Transactional
    public void deleteFollow(String email, UUID followId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Follow follow = followRepository.findById(followId)
                .orElseThrow(() -> new AppException(ErrorCode.FOLLOW_NOT_FOUND));

        // Verify ownership
        if (!follow.getUser().getUserId().equals(user.getUserId())) {
            throw new AppException(ErrorCode.FOLLOW_NOT_FOUND);
        }

        followRepository.delete(follow);
    }

    private void checkFollowLimit(User user) {
        // Researchers have unlimited follows
        if (user.getRole() != null && "researcher".equalsIgnoreCase(user.getRole().getRoleName())) {
            return;
        }

        long count = followRepository.countByUser_UserId(user.getUserId());
        int limit = systemConfigRepository.findByConfigKey("academic_max_follows")
                .map(config -> {
                    try {
                        return Integer.parseInt(config.getConfigValue());
                    } catch (NumberFormatException e) {
                        return 20;
                    }
                })
                .orElse(20);

        if (count >= limit) {
            throw new AppException(ErrorCode.FOLLOW_LIMIT_EXCEEDED);
        }
    }

    private FollowResponse mapToResponse(Follow follow) {
        return FollowResponse.builder()
                .followId(follow.getFollowId())
                .journalId(follow.getJournal() != null ? follow.getJournal().getJournalId() : null)
                .journalName(follow.getJournal() != null ? follow.getJournal().getJournalName() : null)
                .topicId(follow.getTopic() != null ? follow.getTopic().getTopicId() : null)
                .topicName(follow.getTopic() != null ? follow.getTopic().getTopicName() : null)
                .keywordId(follow.getKeyword() != null ? follow.getKeyword().getKeywordId() : null)
                .keywordText(follow.getKeyword() != null ? follow.getKeyword().getKeywordText() : null)
                .notifyEnabled(follow.getNotifyEnabled())
                .createdAt(follow.getCreatedAt())
                .build();
    }

    private WatchlistItemResponse buildWatchlistItem(Follow follow, LocalDateTime weekAgo) {
        if (follow.getKeyword() != null) {
            UUID keywordId = follow.getKeyword().getKeywordId();
            String keywordText = follow.getKeyword().getKeywordText();
            long totalPapers = follow.getKeyword().getPaperCount() != null
                    ? follow.getKeyword().getPaperCount() : 0;
            long newThisWeek = researchPaperRepository.countByKeywordIdAndCreatedAtAfter(keywordId, weekAgo);

            return WatchlistItemResponse.builder()
                    .followId(follow.getFollowId())
                    .targetType("KEYWORD")
                    .targetName(keywordText)
                    .targetId(keywordId)
                    .totalPapers(totalPapers)
                    .newPapersThisWeek(newThisWeek)
                    .notifyEnabled(follow.getNotifyEnabled())
                    .build();
        }

        if (follow.getJournal() != null) {
            UUID journalId = follow.getJournal().getJournalId();
            String journalName = follow.getJournal().getJournalName();
            long totalPapers = researchPaperRepository.countByJournal_JournalId(journalId);
            long newThisWeek = researchPaperRepository.countByJournal_JournalIdAndCreatedAtAfter(journalId, weekAgo);

            return WatchlistItemResponse.builder()
                    .followId(follow.getFollowId())
                    .targetType("JOURNAL")
                    .targetName(journalName)
                    .targetId(journalId)
                    .totalPapers(totalPapers)
                    .newPapersThisWeek(newThisWeek)
                    .notifyEnabled(follow.getNotifyEnabled())
                    .build();
        }

        if (follow.getTopic() != null) {
            UUID topicId = follow.getTopic().getTopicId();
            String topicName = follow.getTopic().getTopicName();
            UUID fieldId = follow.getTopic().getField() != null
                    ? follow.getTopic().getField().getFieldId() : null;
            long totalPapers = fieldId != null
                    ? researchPaperRepository.countByField_FieldId(fieldId) : 0;
            long newThisWeek = fieldId != null
                    ? researchPaperRepository.countByField_FieldIdAndCreatedAtAfter(fieldId, weekAgo) : 0;

            return WatchlistItemResponse.builder()
                    .followId(follow.getFollowId())
                    .targetType("TOPIC")
                    .targetName(topicName)
                    .targetId(topicId)
                    .totalPapers(totalPapers)
                    .newPapersThisWeek(newThisWeek)
                    .notifyEnabled(follow.getNotifyEnabled())
                    .build();
        }

        // Should not happen — every follow has exactly one target
        return WatchlistItemResponse.builder()
                .followId(follow.getFollowId())
                .targetType("UNKNOWN")
                .targetName("Unknown")
                .totalPapers(0L)
                .newPapersThisWeek(0L)
                .notifyEnabled(follow.getNotifyEnabled())
                .build();
    }
}
