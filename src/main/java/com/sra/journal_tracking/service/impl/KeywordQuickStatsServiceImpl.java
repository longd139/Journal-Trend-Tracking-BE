package com.sra.journal_tracking.service.impl;

import com.sra.journal_tracking.constants.KeywordConstants;
import com.sra.journal_tracking.dto.paper.KeywordQuickStatsResponse;
import com.sra.journal_tracking.entity.jpa.ResearchPaper;
import com.sra.journal_tracking.repository.jpa.ResearchPaperRepository;
import com.sra.journal_tracking.service.GraphService;
import com.sra.journal_tracking.service.KeywordQuickStatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Computes quick statistics for a searched keyword by combining
 * Neo4j graph lookups with SQL Server aggregation.
 *
 * Flow:
 * 1. Neo4j → get all paper IDs + count matching the keyword
 * 2. SQL → load papers, compute total citations + avg per paper
 * 3. SQL → count papers by pubYear for YoY growth calculation
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KeywordQuickStatsServiceImpl implements KeywordQuickStatsService {

    private final GraphService graphService;
    private final ResearchPaperRepository researchPaperRepository;

    @Override
    @Transactional(readOnly = true)
    public KeywordQuickStatsResponse getStats(String keyword) {
        String trimmedKeyword = keyword.trim();
        if (trimmedKeyword.isEmpty()) {
            return buildEmptyResponse(keyword);
        }

        // Truncate keyword if too long (defense in depth)
        if (trimmedKeyword.length() > KeywordConstants.MAX_KEYWORD_LENGTH) {
            trimmedKeyword = trimmedKeyword.substring(0, KeywordConstants.MAX_KEYWORD_LENGTH);
        }

        log.info("Computing quick stats for keyword: '{}'", trimmedKeyword);

        // Step 1: Get total paper count from Neo4j (fast aggregate)
        long totalPapers = graphService.countPapersByKeyword(trimmedKeyword);

        if (totalPapers == 0) {
            log.info("No papers found for keyword '{}'", trimmedKeyword);
            return KeywordQuickStatsResponse.builder()
                    .keyword(trimmedKeyword)
                    .totalPapers(0L)
                    .totalCitations(0L)
                    .yoyGrowthRate(null)
                    .yoyGrowthDirection("neutral")
                    .avgCitationsPerPaper(null)
                    .papersThisYear(0L)
                    .papersLastYear(0L)
                    .build();
        }

        // Step 2: Get all paper IDs from Neo4j
        List<String> paperIdStrings = graphService.getAllPaperIdsByKeyword(trimmedKeyword);
        List<UUID> paperIds = paperIdStrings.stream()
                .map(UUID::fromString)
                .toList();

        if (paperIds.isEmpty()) {
            return buildEmptyResponse(trimmedKeyword);
        }

        // Step 3: Sum citations from SQL
        long totalCitations = researchPaperRepository.sumCitationCountByIds(paperIds);

        // Step 4: Compute avg citations per paper
        double avgCitationsPerPaper = (double) totalCitations / totalPapers;

        // Step 5: YoY growth rate (this year vs last year)
        short thisYear = (short) Year.now().getValue();
        short lastYear = (short) (thisYear - 1);

        long papersThisYear = researchPaperRepository.countByPaperIdsAndPubYear(paperIds, thisYear);
        long papersLastYear = researchPaperRepository.countByPaperIdsAndPubYear(paperIds, lastYear);

        Double yoyGrowthRate = null;
        String yoyGrowthDirection = "neutral";

        if (papersLastYear > 0) {
            yoyGrowthRate = ((double) (papersThisYear - papersLastYear) / papersLastYear) * 100.0;
            yoyGrowthDirection = yoyGrowthRate > 0 ? "up" : yoyGrowthRate < 0 ? "down" : "neutral";
        } else if (papersThisYear > 0) {
            // No prior year data but papers exist this year
            yoyGrowthRate = 100.0;
            yoyGrowthDirection = "up";
        }

        KeywordQuickStatsResponse response = KeywordQuickStatsResponse.builder()
                .keyword(trimmedKeyword)
                .totalPapers(totalPapers)
                .totalCitations(totalCitations)
                .yoyGrowthRate(yoyGrowthRate)
                .yoyGrowthDirection(yoyGrowthDirection)
                .avgCitationsPerPaper(roundToOneDecimal(avgCitationsPerPaper))
                .papersThisYear(papersThisYear)
                .papersLastYear(papersLastYear)
                .build();

        log.info("Quick stats for '{}': papers={}, citations={}, yoy={}%, avg={}",
                trimmedKeyword, totalPapers, totalCitations, yoyGrowthRate, response.getAvgCitationsPerPaper());

        return response;
    }

    private KeywordQuickStatsResponse buildEmptyResponse(String keyword) {
        return KeywordQuickStatsResponse.builder()
                .keyword(keyword)
                .totalPapers(0L)
                .totalCitations(0L)
                .yoyGrowthRate(null)
                .yoyGrowthDirection("neutral")
                .avgCitationsPerPaper(null)
                .papersThisYear(0L)
                .papersLastYear(0L)
                .build();
    }

    private Double roundToOneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
