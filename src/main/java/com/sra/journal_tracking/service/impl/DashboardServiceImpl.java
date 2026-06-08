package com.sra.journal_tracking.service.impl;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sra.journal_tracking.dto.dashboard.OverviewStatsResponse;
import com.sra.journal_tracking.dto.dashboard.TotalPapersResponse;
import com.sra.journal_tracking.entity.jpa.PublicationTrend;
import com.sra.journal_tracking.entity.jpa.ResearchTopic;
import com.sra.journal_tracking.repository.jpa.PublicationTrendRepository;
import com.sra.journal_tracking.repository.jpa.ResearchPaperRepository;
import com.sra.journal_tracking.repository.jpa.ResearchTopicRepository;
import com.sra.journal_tracking.service.DashboardService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardServiceImpl implements DashboardService {

    private final ResearchPaperRepository researchPaperRepository;
    private final ResearchTopicRepository researchTopicRepository;
    private final PublicationTrendRepository publicationTrendRepository;

    @Override
    public OverviewStatsResponse getOverviewStats() {
        log.info("Fetching overview dashboard statistics");

        // Card 1: Total papers tracked
        long papersTracked = researchPaperRepository.count();

        // Card 2: Total citations across all papers
        Long totalCitations = researchPaperRepository.sumTotalCitations();

        // Card 3: Number of trending topics
        long topTrendingNow = researchTopicRepository.countByIsTrendingTrue();

        // Card 4: Top growth topic (highest GrowthRate from PUBLICATION_TREND)
        OverviewStatsResponse.GrowthTopicInfo topGrowthTopic = buildTopGrowthTopic();

        OverviewStatsResponse response = OverviewStatsResponse.builder()
                .papersTracked(papersTracked)
                .totalCitations(totalCitations)
                .topTrendingNow(topTrendingNow)
                .topGrowthTopic(topGrowthTopic)
                .build();

        log.info("Overview stats: papers={}, citations={}, trending={}, topGrowthTopic={}",
                papersTracked, totalCitations, topTrendingNow,
                topGrowthTopic != null ? topGrowthTopic.getTopicName() : "N/A");

        return response;
    }

    @Override
    public TotalPapersResponse getTotalPapers() {
        long count = researchPaperRepository.count();
        log.info("Total papers in system: {}", count);
        return TotalPapersResponse.builder()
                .totalPapers(count)
                .build();
    }

    private OverviewStatsResponse.GrowthTopicInfo buildTopGrowthTopic() {
        Optional<PublicationTrend> topTrendOpt = publicationTrendRepository.findTopGrowthTopic();

        if (topTrendOpt.isEmpty()) {
            log.info("No publication trend data found for topics");
            return null;
        }

        PublicationTrend topTrend = topTrendOpt.get();

        // Resolve the topic name from ResearchTopic using targetId
        String topicName = researchTopicRepository.findById(topTrend.getTargetId())
                .map(ResearchTopic::getTopicName)
                .orElse("Unknown Topic");

        return OverviewStatsResponse.GrowthTopicInfo.builder()
                .topicName(topicName)
                .growthRate(topTrend.getGrowthRate())
                .paperCount(topTrend.getPaperCount())
                .calculatedAt(topTrend.getCalculatedAt())
                .build();
    }
}
