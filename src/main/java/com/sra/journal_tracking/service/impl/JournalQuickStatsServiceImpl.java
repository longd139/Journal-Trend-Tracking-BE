package com.sra.journal_tracking.service.impl;

import com.sra.journal_tracking.dto.journal.JournalAuthorResponse;
import com.sra.journal_tracking.dto.journal.JournalQuickStatsResponse;
import com.sra.journal_tracking.dto.journal.JournalTimelineResponse;
import com.sra.journal_tracking.dto.journal.JournalTimelineResponse.YearlyDataPoint;
import com.sra.journal_tracking.dto.paper.KeywordDTO;
import com.sra.journal_tracking.dto.paper.PaperDetailResponseDTO;
import com.sra.journal_tracking.entity.jpa.Journal;
import com.sra.journal_tracking.entity.jpa.ResearchPaper;
import com.sra.journal_tracking.repository.jpa.JournalRepository;
import com.sra.journal_tracking.repository.jpa.ResearchPaperRepository;
import com.sra.journal_tracking.service.JournalQuickStatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Computes journal KPIs by combining Journal metadata with
 * paper-count and citation aggregation from the ResearchPaper table.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JournalQuickStatsServiceImpl implements JournalQuickStatsService {

    private final JournalRepository journalRepository;
    private final ResearchPaperRepository researchPaperRepository;

    @Override
    @Transactional(readOnly = true)
    public JournalQuickStatsResponse getStats(String journalName) {
        String trimmed = journalName.trim();
        if (trimmed.isEmpty()) {
            return buildEmptyResponse(journalName);
        }

        log.info("Computing journal quick stats for: '{}'", trimmed);

        // Step 1: Search journal by name (fuzzy match)
        List<Journal> journals = journalRepository.searchByName(trimmed, PageRequest.of(0, 1));
        if (journals.isEmpty()) {
            log.info("No journal found matching '{}'", trimmed);
            return buildEmptyResponse(trimmed);
        }

        Journal journal = journals.get(0);
        log.info("Matched journal: '{}' (IF={}, Q={})", journal.getJournalName(),
                journal.getImpactFactor(), journal.getQuartile());

        // Step 2: Aggregate paper stats
        long totalPapers = researchPaperRepository.countByJournal_JournalId(journal.getJournalId());
        long totalCitations = researchPaperRepository.sumCitationsByJournalId(journal.getJournalId());

        Double avgCitations = totalPapers > 0
                ? Math.round((double) totalCitations / totalPapers * 10.0) / 10.0
                : null;

        // Step 3: Calculated CiteScore (internal metric, fallback when IF is null)
        Double calculatedCiteScore = totalPapers > 0
                ? Math.round((double) totalCitations / totalPapers * 100.0) / 100.0
                : null;

        // Step 4: Top keywords for this journal
        List<String> topKeywords = getTopKeywords(journal.getJournalId());

        return JournalQuickStatsResponse.builder()
                .journalId(journal.getJournalId().toString())
                .journalName(journal.getJournalName())
                .issn(journal.getIssn())
                .publisher(journal.getPublisher())
                .impactFactor(journal.getImpactFactor())
                .calculatedCiteScore(calculatedCiteScore)
                .quartile(journal.getQuartile())
                .totalPapers(totalPapers)
                .totalCitations(totalCitations)
                .avgCitationsPerPaper(avgCitations)
                .topKeywords(topKeywords)
                .build();
    }

    private List<String> getTopKeywords(java.util.UUID journalId) {
        try {
            List<Object[]> rows = researchPaperRepository.findTopKeywordsByJournalId(
                    journalId, PageRequest.of(0, 5));
            return rows.stream()
                    .map(row -> (String) row[0])
                    .toList();
        } catch (Exception e) {
            log.warn("Failed to fetch top keywords for journal {}: {}", journalId, e.getMessage());
            return List.of();
        }
    }

    private JournalQuickStatsResponse buildEmptyResponse(String query) {
        return JournalQuickStatsResponse.builder()
                .journalName(query)
                .totalPapers(0L)
                .totalCitations(0L)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public JournalTimelineResponse getTimeline(String journalName) {
        String trimmed = journalName.trim();
        if (trimmed.isEmpty()) {
            return JournalTimelineResponse.builder()
                    .journalName(journalName)
                    .timeline(List.of())
                    .build();
        }

        log.info("Computing journal timeline for: '{}'", trimmed);

        // Step 1: Find the journal
        List<Journal> journals = journalRepository.searchByName(trimmed, PageRequest.of(0, 1));
        if (journals.isEmpty()) {
            log.info("No journal found matching '{}'", trimmed);
            return JournalTimelineResponse.builder()
                    .journalName(trimmed)
                    .timeline(List.of())
                    .build();
        }

        Journal journal = journals.get(0);

        // Step 2: Get yearly stats for last 10 years
        short thisYear = (short) Year.now().getValue();
        short startYear = (short) (thisYear - 9); // 10 years inclusive

        List<Object[]> rows = researchPaperRepository.getJournalYearlyStats(
                journal.getJournalId(), startYear);

        // Step 3: Map to YearlyDataPoint list
        List<YearlyDataPoint> timeline = new ArrayList<>();
        long totalPapers = 0;
        long totalCitations = 0;

        for (Object[] row : rows) {
            Short year = (Short) row[0];
            Long paperCount = ((Number) row[1]).longValue();
            Long citationCount = ((Number) row[2]).longValue();

            Double avgCitations = paperCount > 0
                    ? Math.round((double) citationCount / paperCount * 10.0) / 10.0
                    : null;

            timeline.add(YearlyDataPoint.builder()
                    .year(year.intValue())
                    .paperCount(paperCount)
                    .citationCount(citationCount)
                    .avgCitationsPerPaper(avgCitations)
                    .build());

            totalPapers += paperCount;
            totalCitations += citationCount;
        }

        log.info("Journal timeline for '{}': {} years of data, {} total papers",
                journal.getJournalName(), timeline.size(), totalPapers);

        return JournalTimelineResponse.builder()
                .journalId(journal.getJournalId().toString())
                .journalName(journal.getJournalName())
                .issn(journal.getIssn())
                .publisher(journal.getPublisher())
                .impactFactor(journal.getImpactFactor())
                .quartile(journal.getQuartile())
                .totalPapers(totalPapers)
                .totalCitations(totalCitations)
                .timeline(timeline)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaperDetailResponseDTO> getTopPapers(String journalName) {
        Journal journal = findJournal(journalName);
        if (journal == null) return List.of();

        List<ResearchPaper> papers = researchPaperRepository.findTopCitedByJournalId(
                journal.getJournalId(), PageRequest.of(0, 5));

        return papers.stream()
                .map(this::mapToSummaryDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<JournalAuthorResponse> getTopAuthors(String journalName) {
        Journal journal = findJournal(journalName);
        if (journal == null) return List.of();

        List<Object[]> rows = researchPaperRepository.findTopAuthorsByJournalId(
                journal.getJournalId(), PageRequest.of(0, 10));

        return rows.stream()
                .map(row -> {
                    long paperCount = ((Number) row[2]).longValue();
                    long totalCitations = ((Number) row[3]).longValue();
                    Double avgCitations = paperCount > 0
                            ? Math.round((double) totalCitations / paperCount * 10.0) / 10.0
                            : null;

                    return JournalAuthorResponse.builder()
                            .authorName((String) row[0])
                            .openAlexId((String) row[1])
                            .paperCount(paperCount)
                            .totalCitations(totalCitations)
                            .avgCitationsPerPaper(avgCitations)
                            .build();
                })
                .toList();
    }

    // ── Helpers ──

    /** Find journal by fuzzy name search, returning null if not found. */
    private Journal findJournal(String journalName) {
        String trimmed = journalName.trim();
        if (trimmed.isEmpty()) return null;
        List<Journal> journals = journalRepository.searchByName(trimmed, PageRequest.of(0, 1));
        return journals.isEmpty() ? null : journals.get(0);
    }

    /** Lightweight DTO mapping for paper list view. */
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
                .toList() : List.of();

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
}
