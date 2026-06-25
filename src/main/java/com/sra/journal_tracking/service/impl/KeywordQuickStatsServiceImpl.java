package com.sra.journal_tracking.service.impl;

import com.sra.journal_tracking.constants.KeywordConstants;
import com.sra.journal_tracking.dto.paper.AuthorDTO;
import com.sra.journal_tracking.dto.paper.KeywordDTO;
import com.sra.journal_tracking.dto.paper.KeywordQuickStatsResponse;
import com.sra.journal_tracking.dto.paper.PaperDetailResponseDTO;
import com.sra.journal_tracking.dto.paper.RelatedKeywordResponse;
import com.sra.journal_tracking.dto.paper.TopJournalResponse;
import com.sra.journal_tracking.entity.jpa.ResearchPaper;
import com.sra.journal_tracking.repository.jpa.ResearchPaperRepository;
import com.sra.journal_tracking.service.GraphService;
import com.sra.journal_tracking.service.KeywordQuickStatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Year;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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
                    .topJournals(List.of())
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

        // Step 6: Top journals by keyword (horizontal bar chart)
        List<TopJournalResponse> topJournals = getTopJournals(paperIds);

        KeywordQuickStatsResponse response = KeywordQuickStatsResponse.builder()
                .keyword(trimmedKeyword)
                .totalPapers(totalPapers)
                .totalCitations(totalCitations)
                .yoyGrowthRate(yoyGrowthRate)
                .yoyGrowthDirection(yoyGrowthDirection)
                .avgCitationsPerPaper(roundToOneDecimal(avgCitationsPerPaper))
                .papersThisYear(papersThisYear)
                .papersLastYear(papersLastYear)
                .topJournals(topJournals)
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
                .topJournals(List.of())
                .build();
    }

    /**
     * Fetch top 10 journals by paper count for a given set of paper IDs.
     */
    private List<TopJournalResponse> getTopJournals(List<UUID> paperIds) {
        try {
            List<Object[]> rows = researchPaperRepository.findTopJournalsByPaperIds(
                    paperIds, PageRequest.of(0, 10));

            return rows.stream()
                    .map(row -> TopJournalResponse.builder()
                            .journalName((String) row[0])
                            .paperCount(((Number) row[4]).longValue())
                            .impactFactor((BigDecimal) row[1])
                            .quartile((String) row[2])
                            .publisher((String) row[3])
                            .build())
                    .toList();
        } catch (Exception e) {
            log.warn("Failed to compute top journals: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public List<RelatedKeywordResponse> getRelatedTrends(String keyword) {
        String trimmedKeyword = keyword.trim();
        if (trimmedKeyword.isEmpty()) {
            return List.of();
        }

        if (trimmedKeyword.length() > KeywordConstants.MAX_KEYWORD_LENGTH) {
            trimmedKeyword = trimmedKeyword.substring(0, KeywordConstants.MAX_KEYWORD_LENGTH);
        }

        short thisYear = (short) Year.now().getValue();
        short lastYear = (short) (thisYear - 1);
        int startYear = thisYear - 2; // last 2 years

        log.info("Computing related trends for keyword: '{}' ({}–{})", trimmedKeyword, startYear, thisYear);

        // Step 1: Get co-occurring keywords from Neo4j (with year breakdowns)
        List<Map<String, Object>> neo4jResults = graphService.getCooccurringKeywords(
                trimmedKeyword.toLowerCase(), startYear, thisYear, lastYear, 10);

        if (neo4jResults.isEmpty()) {
            log.info("No related keywords found for '{}'", trimmedKeyword);
            return List.of();
        }

        // Step 2: Map to DTOs with growth rate calculation
        return neo4jResults.stream()
                .map(row -> {
                    long total = (long) row.get("totalCount");
                    long thisYr = (long) row.get("thisYearCount");
                    long lastYr = (long) row.get("lastYearCount");

                    Double growthRate = null;
                    String growthDirection = "neutral";

                    if (lastYr > 0) {
                        growthRate = ((double) (thisYr - lastYr) / lastYr) * 100.0;
                        growthRate = roundToOneDecimal(growthRate);
                        growthDirection = growthRate > 0 ? "up" : growthRate < 0 ? "down" : "neutral";
                    } else if (thisYr > 0) {
                        growthRate = 100.0;
                        growthDirection = "up";
                    }

                    return RelatedKeywordResponse.builder()
                            .keyword((String) row.get("originalKeyword"))
                            .normalizedKeyword((String) row.get("normalizedKeyword"))
                            .cooccurrenceCount(total)
                            .thisYearCount(thisYr)
                            .lastYearCount(lastYr)
                            .growthRate(growthRate)
                            .growthDirection(growthDirection)
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<PaperDetailResponseDTO> getTopInfluentialPapers(String keyword) {
        String trimmedKeyword = keyword.trim();
        if (trimmedKeyword.isEmpty()) {
            return List.of();
        }

        if (trimmedKeyword.length() > KeywordConstants.MAX_KEYWORD_LENGTH) {
            trimmedKeyword = trimmedKeyword.substring(0, KeywordConstants.MAX_KEYWORD_LENGTH);
        }

        log.info("Fetching top influential papers for keyword: '{}'", trimmedKeyword);

        // Step 1: Get all paper IDs from Neo4j
        List<String> paperIdStrings = graphService.getAllPaperIdsByKeyword(trimmedKeyword.toLowerCase());
        if (paperIdStrings.isEmpty()) {
            log.info("No papers found for '{}'", trimmedKeyword);
            return List.of();
        }

        List<UUID> paperIds = paperIdStrings.stream()
                .map(UUID::fromString)
                .toList();

        // Step 2: Fetch papers from SQL, ordered by citation count DESC, top 5
        List<ResearchPaper> topPapers = researchPaperRepository.findTopCitedByIds(
                paperIds, PageRequest.of(0, 5));

        // Step 3: Map to DTOs
        return topPapers.stream()
                .map(this::mapToSummaryDTO)
                .collect(Collectors.toList());
    }

    /**
     * Lightweight DTO mapping for top-influential-papers list view.
     * Includes essential fields only (skips full authors to keep payload light).
     */
    private PaperDetailResponseDTO mapToSummaryDTO(ResearchPaper paper) {
        List<KeywordDTO> keywords = paper.getKeywords() != null ? paper.getKeywords().stream()
                .filter(pk -> pk.getRelevanceScore() == null || pk.getRelevanceScore() != 1.0)
                .sorted(Comparator.comparing(
                        pk -> pk.getRelevanceScore() != null ? pk.getRelevanceScore() : 0.0d,
                        Comparator.reverseOrder()))
                .map(pk -> KeywordDTO.builder()
                        .keywordText(pk.getKeyword().getKeywordText())
                        .relevanceScore(pk.getRelevanceScore())
                        .build())
                .collect(Collectors.toList()) : List.of();

        return PaperDetailResponseDTO.builder()
                .paperId(paper.getPaperId())
                .title(paper.getTitle())
                .abstractText(paper.getAbstractText())
                .doi(paper.getDoi())
                .pubYear(paper.getPubYear())
                .pubDate(paper.getPubDate())
                .citationCount(paper.getCitationCount())
                .isOpenAccess(paper.getIsOpenAccess())
                .journalName(paper.getJournal() != null ? paper.getJournal().getJournalName() : null)
                .sourceUrl(paper.getDoi() != null ? "https://doi.org/" + paper.getDoi() : null)
                .pdfAvailable(Boolean.TRUE.equals(paper.getIsOpenAccess())
                        || (paper.getPdfUrl() != null && !paper.getPdfUrl().isBlank()))
                .pdfUrl(paper.getPdfUrl())
                .keywords(keywords)
                .createdAt(paper.getCreatedAt())
                .build();
    }

    private Double roundToOneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
