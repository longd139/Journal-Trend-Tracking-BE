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
                .topTrendingNow(buildTopTrendingCard())
                .topGrowthTopic(buildTopGrowthTopicCard())
                .build();
    }

    // Card 1: Papers created this month vs last month
    private StatCard buildPapersTrackedCard(LocalDateTime currentMonthStart,
                                            LocalDateTime previousMonthStart,
                                            LocalDateTime now) {
        long currentMonthCount = researchPaperRepository.countByCreatedAtBetween(currentMonthStart, now);
        long previousMonthCount = researchPaperRepository.countByCreatedAtBetween(previousMonthStart, currentMonthStart);

        Double growthPercent = calculateGrowthPercent(currentMonthCount, previousMonthCount);

        return StatCard.builder()
                .label("New Papers")
                .value(currentMonthCount)
                .growthPercent(growthPercent)
                .growthLabel("vs last month")
                .growthDirection(deriveGrowthDirection(growthPercent))
                .build();
    }

    // Card 2: Citations of papers created this month vs last month
    private StatCard buildTotalCitationsCard(LocalDateTime currentMonthStart,
                                             LocalDateTime previousMonthStart,
                                             LocalDateTime now) {
        long currentMonthCitations = researchPaperRepository.sumCitationCountsByCreatedAtBetween(currentMonthStart, now);
        long previousMonthCitations = researchPaperRepository.sumCitationCountsByCreatedAtBetween(previousMonthStart, currentMonthStart);

        Double growthPercent = calculateGrowthPercent(currentMonthCitations, previousMonthCitations);

        return StatCard.builder()
                .label("New Citations")
                .value(currentMonthCitations)
                .growthPercent(growthPercent)
                .growthLabel("vs last month")
                .growthDirection(deriveGrowthDirection(growthPercent))
                .build();
    }

    // Card 3: Currently trending topics count (no growth — ResearchTopic lacks createdAt)
    private StatCard buildTopTrendingCard() {
        long trendingCount = researchTopicRepository.countByIsTrendingTrue();

        return StatCard.builder()
                .label("Trending Topics")
                .value(trendingCount)
                .growthPercent(null)
                .growthLabel("currently active")
                .growthDirection("neutral")
                .build();
    }

    // Card 4: Topic with highest trend score
    private StatCard buildTopGrowthTopicCard() {
        return researchTopicRepository.findTopByIsTrendingTrueOrderByTrendScoreDesc()
                .map(this::mapTopicToStatCard)
                .orElse(StatCard.builder()
                        .label("Top Growth Topic")
                        .value(0L)
                        .growthPercent(null)
                        .growthLabel("no trending topics")
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
                .growthLabel("trend score")
                .growthDirection(deriveGrowthDirection(growthPercent))
                .topicName(topic.getTopicName())
                .build();
    }

    private Double calculateGrowthPercent(long current, long previous) {
        if (previous == 0) {
            return current > 0 ? 100.0 : 0.0;
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
