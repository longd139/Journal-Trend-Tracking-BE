package com.sra.journal_tracking.service.impl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sra.journal_tracking.dto.overview.OverviewStatisticsResponse;
import com.sra.journal_tracking.dto.overview.OverviewStatisticsResponse.StatCard;
import com.sra.journal_tracking.entity.jpa.ResearchTopic;
import com.sra.journal_tracking.repository.jpa.ResearchPaperRepository;
import com.sra.journal_tracking.repository.jpa.ResearchTopicRepository;
import com.sra.journal_tracking.service.OverviewStatisticsService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OverviewStatisticsServiceImpl implements OverviewStatisticsService {

    private final ResearchPaperRepository researchPaperRepository;
    private final ResearchTopicRepository researchTopicRepository;

    @Override
    @Transactional(readOnly = true)
    public OverviewStatisticsResponse getStatistics() {
        YearMonth currentMonth = YearMonth.now();
        YearMonth previousMonth = currentMonth.minusMonths(1);

        LocalDateTime currentMonthStart = currentMonth.atDay(1).atStartOfDay();
        LocalDateTime previousMonthStart = previousMonth.atDay(1).atStartOfDay();
        LocalDateTime now = LocalDateTime.now();

        return OverviewStatisticsResponse.builder()
                .papersTracked(buildPapersTrackedCard(currentMonthStart, previousMonthStart, now))
                .totalCitations(buildTotalCitationsCard(currentMonthStart, previousMonthStart, now))
                .topTrendingNow(buildTopTrendingCard(currentMonthStart, previousMonthStart, now))
                .topGrowthTopic(buildTopGrowthTopicCard())
                .build();
    }

    private StatCard buildPapersTrackedCard(LocalDateTime currentMonthStart,
                                            LocalDateTime previousMonthStart,
                                            LocalDateTime now) {
        long total = researchPaperRepository.count();
        long currentMonthCount = researchPaperRepository.countByCreatedAtBetween(currentMonthStart, now);
        long previousMonthCount = researchPaperRepository.countByCreatedAtBetween(previousMonthStart, currentMonthStart);

        Double growthPercent = calculateGrowthPercent(currentMonthCount, previousMonthCount);

        return StatCard.builder()
                .label("Papers Tracked")
                .value(total)
                .growthPercent(growthPercent)
                .growthLabel("vs last month")
                .growthDirection(deriveGrowthDirection(growthPercent))
                .build();
    }

    private StatCard buildTotalCitationsCard(LocalDateTime currentMonthStart,
                                             LocalDateTime previousMonthStart,
                                             LocalDateTime now) {
        long totalCitations = researchPaperRepository.sumAllCitationCounts();
        long currentMonthCitations = researchPaperRepository.sumCitationCountsByCreatedAtBetween(currentMonthStart, now);
        long previousMonthCitations = researchPaperRepository.sumCitationCountsByCreatedAtBetween(previousMonthStart, currentMonthStart);

        Double growthPercent = calculateGrowthPercent(currentMonthCitations, previousMonthCitations);

        return StatCard.builder()
                .label("Total Citations")
                .value(totalCitations)
                .growthPercent(growthPercent)
                .growthLabel("vs last month")
                .growthDirection(deriveGrowthDirection(growthPercent))
                .build();
    }

    private StatCard buildTopTrendingCard(LocalDateTime currentMonthStart,
                                          LocalDateTime previousMonthStart,
                                          LocalDateTime now) {
        long trendingCount = researchTopicRepository.countByIsTrendingTrue();
        long currentMonthTrending = researchTopicRepository.countTrendingByUpdatedAtBetween(currentMonthStart, now);
        long previousMonthTrending = researchTopicRepository.countTrendingByUpdatedAtBetween(previousMonthStart, currentMonthStart);

        Double growthPercent = calculateGrowthPercent(currentMonthTrending, previousMonthTrending);

        return StatCard.builder()
                .label("Top Trending Now")
                .value(trendingCount)
                .growthPercent(growthPercent)
                .growthLabel("vs last month")
                .growthDirection(deriveGrowthDirection(growthPercent))
                .build();
    }

    private StatCard buildTopGrowthTopicCard() {
        return researchTopicRepository.findTopByIsTrendingTrueOrderByTrendScoreDesc()
                .map(this::mapTopicToStatCard)
                .orElse(StatCard.builder()
                        .label("Top Growth Topic")
                        .value(0L)
                        .growthPercent(null)
                        .growthLabel("highest growth rate")
                        .growthDirection("neutral")
                        .build());
    }

    private StatCard mapTopicToStatCard(ResearchTopic topic) {
        BigDecimal trendScore = topic.getTrendScore();
        Double growthPercent = trendScore != null ? trendScore.doubleValue() : null;

        return StatCard.builder()
                .label("Top Growth Topic")
                .value(topic.getPaperCount() != null ? topic.getPaperCount().longValue() : 0L)
                .growthPercent(growthPercent)
                .growthLabel("highest growth rate")
                .growthDirection(deriveGrowthDirection(growthPercent))
                .topicName(topic.getTopicName())
                .build();
    }

    private Double calculateGrowthPercent(long current, long previous) {
        if (previous == 0) {
            return current > 0 ? null : 0.0; // null = can't calculate, 0.0 = both zero
        }
        return ((double) (current - previous) / previous) * 100.0;
    }

    private String deriveGrowthDirection(Double growthPercent) {
        if (growthPercent == null) {
            return "neutral";
        }
        if (growthPercent > 0) {
            return "up";
        }
        if (growthPercent < 0) {
            return "down";
        }
        return "neutral";
    }
}
