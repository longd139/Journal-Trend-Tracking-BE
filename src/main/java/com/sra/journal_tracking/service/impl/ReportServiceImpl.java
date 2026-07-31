package com.sra.journal_tracking.service.impl;

import com.sra.journal_tracking.dto.author.AuthorQuickStatsResponse;
import com.sra.journal_tracking.dto.journal.JournalQuickStatsResponse;
import com.sra.journal_tracking.dto.paper.PaperDetailResponseDTO;
import com.sra.journal_tracking.dto.report.AuthorImpactReportResponse;
import com.sra.journal_tracking.dto.report.JournalQualityReportResponse;
import com.sra.journal_tracking.dto.report.KeywordTrendHistoryItem;
import com.sra.journal_tracking.dto.report.KeywordTrendReportResponse;
import com.sra.journal_tracking.entity.jpa.Author;
import com.sra.journal_tracking.entity.jpa.Journal;
import com.sra.journal_tracking.entity.jpa.KeywordTrendCache;
import com.sra.journal_tracking.entity.jpa.ResearchField;
import com.sra.journal_tracking.exception.AppException;
import com.sra.journal_tracking.exception.ErrorCode;
import com.sra.journal_tracking.repository.jpa.AuthorRepository;
import com.sra.journal_tracking.repository.jpa.JournalRepository;
import com.sra.journal_tracking.repository.jpa.KeywordTrendCacheRepository;
import com.sra.journal_tracking.repository.jpa.PaperAuthorRepository;
import com.sra.journal_tracking.repository.jpa.ResearchFieldRepository;
import com.sra.journal_tracking.repository.jpa.ResearchPaperRepository;
import com.sra.journal_tracking.service.AuthorQuickStatsService;
import com.sra.journal_tracking.service.GraphService;
import com.sra.journal_tracking.service.JournalQuickStatsService;
import com.sra.journal_tracking.service.OpenAlexFallbackSearchService;
import com.sra.journal_tracking.service.ReportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Report generation service — produces analytical reports for keywords, authors, and journals.
 * Reuses existing infrastructure: GraphService (Neo4j), Quick Stats services (SQL + OpenAlex),
 * and repository aggregation queries.
 * <p>
 * Insight text is template-generated in Vietnamese — no external AI call needed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private static final int KEYWORD_TREND_YEAR_WINDOW = 5;
    private static final int JOURNAL_RECENT_YEAR_WINDOW = 2;
    private static final int AUTHOR_ACTIVITY_YEAR_WINDOW = 3;
    private static final long LOCAL_DATA_MIN_THRESHOLD = 500; // below this, use OpenAlex for details too

    private final GraphService graphService;
    private final AuthorQuickStatsService authorQuickStatsService;
    private final JournalQuickStatsService journalQuickStatsService;
    private final ResearchPaperRepository researchPaperRepository;
    private final AuthorRepository authorRepository;
    private final JournalRepository journalRepository;
    private final PaperAuthorRepository paperAuthorRepository;
    private final ResearchFieldRepository researchFieldRepository;
    private final KeywordTrendCacheRepository keywordTrendCacheRepository;
    private final ObjectMapper objectMapper;
    private final OpenAlexFallbackSearchService openAlexSearchService;

    // ============================================
    //  LANGUAGE HELPER
    // ============================================

    private boolean isEnglish(String lang) {
        return lang != null && lang.equalsIgnoreCase("en");
    }

    // ============================================
    //  KEYWORD TREND REPORT
    // ============================================

    @Override
    @Transactional
    @Cacheable(value = "search:report", cacheManager = "searchCacheManager",
            key = "'keywordTrend:' + #keyword.trim().toLowerCase() + ':' + (#startYear != null ? #startYear : '') + ':' + (#endYear != null ? #endYear : '')",
            unless = "#result == null || #result.summary == null || #result.summary.totalPublications == 0")
    public KeywordTrendReportResponse getKeywordTrendReport(String keyword,
                                                              Integer startYear, Integer endYear, String lang) {
        String trimmed = keyword.trim();
        if (trimmed.isEmpty()) {
            return buildEmptyKeywordReport(keyword, lang);
        }

        log.info("Generating keyword trend report for: '{}' (y={}-{})", trimmed, startYear, endYear);

        short thisYear = endYear != null ? endYear.shortValue() : currentYear();
        short trendStartYear = startYear != null ? startYear.shortValue()
                : (short) 1900; // no user filter → show all years

        // Step 1: Get total papers count from OpenAlex (always accurate)
        long totalPapers = openAlexSearchService.getKeywordTotalCount(trimmed, startYear, endYear);
        if (totalPapers == 0) {
            log.info("No papers found for '{}'", trimmed);
            return buildEmptyKeywordReport(trimmed, lang);
        }

        // Step 2: Try SQL for paper IDs (via keyword join)
        List<UUID> paperIds = List.of();
        try {
            var sqlPapers = researchPaperRepository.findTopCitedByKeywordExact(
                    trimmed, PageRequest.of(0, 200));
            if (!sqlPapers.isEmpty()) {
                paperIds = sqlPapers.stream()
                        .map(com.sra.journal_tracking.entity.jpa.ResearchPaper::getPaperId)
                        .toList();
            }
        } catch (Exception e) {
            log.warn("SQL keyword lookup failed for '{}': {}", trimmed, e.getMessage());
        }
        long localPaperCount = paperIds.size();
        boolean useLocalDetails = localPaperCount >= LOCAL_DATA_MIN_THRESHOLD;

        log.info("Report for '{}': openAlexTotal={}, localPapers={}, useLocalDetails={}",
                trimmed, totalPapers, localPaperCount, useLocalDetails);

        // Step 3: Build report — OpenAlex for total count, SQL or OpenAlex for details
        KeywordTrendReportResponse.Summary summary;
        List<KeywordTrendReportResponse.TrendPoint> publicationTrend;
        List<KeywordTrendReportResponse.TrendPoint> citationTrend;
        List<KeywordTrendReportResponse.CoOccurringKeyword> coOccurringKeywords;
        List<KeywordTrendReportResponse.TopJournal> topJournals;

        if (useLocalDetails) {
            // ── SQL has enough data — use it for details ──
            short lastYear = (short) (thisYear - 1);
            summary = buildSummary(trimmed, paperIds, totalPapers);
            publicationTrend = buildPublicationTrend(paperIds, trendStartYear);
            citationTrend = buildCitationTrend(paperIds, trendStartYear);
            coOccurringKeywords = buildCoOccurringKeywordsFromSql(paperIds);
            topJournals = buildTopJournals(paperIds);
        } else {
            // ── Not enough SQL data — use OpenAlex for everything ──
            var topPapers = openAlexSearchService.searchTopCited(trimmed, 50, startYear, endYear);

            // Publication trend from OpenAlex group_by
            publicationTrend = openAlexSearchService.getKeywordYearlyBreakdown(
                    trimmed, trendStartYear, startYear, endYear).stream()
                    .filter(yc -> yc.year() >= trendStartYear && yc.year() <= thisYear)
                    .map(yc -> KeywordTrendReportResponse.TrendPoint.builder()
                            .year(yc.year()).count(yc.count()).build())
                    .toList();

            // Citation trend from OpenAlex counts_by_year
            citationTrend = openAlexSearchService.getAggregatedCitationTrend(
                    trimmed, trendStartYear, startYear, endYear).stream()
                    .map(yc -> KeywordTrendReportResponse.TrendPoint.builder()
                            .year(yc.year()).count(yc.count()).build())
                    .toList();

            // Co-occurring keywords from top papers
            coOccurringKeywords = extractCoKeywordsFromPapers(topPapers);

            // Top journals from top papers
            topJournals = extractTopJournalsFromPapers(topPapers);

            // Summary from top paper + OpenAlex total
            var topPaper = !topPapers.isEmpty() ? topPapers.get(0) : null;
            summary = KeywordTrendReportResponse.Summary.builder()
                    .totalPublications(totalPapers)
                    .totalCitations(topPaper != null && topPaper.getCitationCount() != null
                            ? topPaper.getCitationCount().longValue() : 0)
                    .peakYear(topPaper != null && topPaper.getPubYear() != null
                            ? topPaper.getPubYear().intValue() : null)
                    .topJournal(topPaper != null && topPaper.getJournalName() != null
                            ? KeywordTrendReportResponse.TopJournalInfo.builder()
                                    .name(topPaper.getJournalName()).paperCount(1).build()
                            : null)
                    .build();
        }

        String insight = generateKeywordInsight(trimmed, summary, lang);

        String reportTitle = isEnglish(lang)
                ? "Trend Report: " + trimmed
                : "Báo cáo xu hướng: " + trimmed;

        KeywordTrendReportResponse report = KeywordTrendReportResponse.builder()
                .keyword(trimmed)
                .reportTitle(reportTitle)
                .publicationTrend(publicationTrend)
                .citationTrend(citationTrend)
                .coOccurringKeywords(coOccurringKeywords)
                .topJournals(topJournals)
                .insight(insight)
                .build();

        saveKeywordTrendToDb(trimmed, report);
        return report;
    }

    // ── OpenAlex extraction helpers ──

    private List<KeywordTrendReportResponse.CoOccurringKeyword> extractCoKeywordsFromPapers(
            List<PaperDetailResponseDTO> papers) {
        if (papers == null || papers.isEmpty()) return List.of();
        Map<String, Long> kwCounts = new java.util.LinkedHashMap<>();
        for (var p : papers) {
            if (p.getKeywords() != null) {
                for (var kw : p.getKeywords()) {
                    if (kw.getKeywordText() != null && !kw.getKeywordText().isBlank()) {
                        kwCounts.merge(kw.getKeywordText().toLowerCase(), 1L, Long::sum);
                    }
                }
            }
        }
        return kwCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(8)
                .map(e -> KeywordTrendReportResponse.CoOccurringKeyword.builder()
                        .keyword(e.getKey()).count(e.getValue()).build())
                .toList();
    }

    private List<KeywordTrendReportResponse.TopJournal> extractTopJournalsFromPapers(
            List<com.sra.journal_tracking.dto.paper.PaperDetailResponseDTO> papers) {
        if (papers == null || papers.isEmpty()) return List.of();
        Map<String, Long> jCounts = new java.util.LinkedHashMap<>();
        for (var p : papers) {
            if (p.getJournalName() != null && !p.getJournalName().isBlank()) {
                jCounts.merge(p.getJournalName(), 1L, Long::sum);
            }
        }
        return jCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(6)
                .map(e -> KeywordTrendReportResponse.TopJournal.builder()
                        .name(e.getKey()).count(e.getValue()).build())
                .toList();
    }

    // ── SQL-based helpers (no Neo4j dependency) ──

    private List<KeywordTrendReportResponse.CoOccurringKeyword> buildCoOccurringKeywordsFromSql(
            List<UUID> paperIds) {
        if (paperIds.isEmpty()) return List.of();
        try {
            List<Object[]> rows = researchPaperRepository.findTopKeywordsByPaperIds(
                    paperIds, PageRequest.of(0, 8));
            return rows.stream()
                    .map(row -> KeywordTrendReportResponse.CoOccurringKeyword.builder()
                            .keyword((String) row[0])
                            .count(((Number) row[1]).longValue())
                            .build())
                    .toList();
        } catch (Exception e) {
            log.warn("Failed to get SQL co-occurring keywords: {}", e.getMessage());
            return List.of();
        }
    }

    // ============================================
    //  AUTHOR IMPACT REPORT
    // ============================================

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "search:report", cacheManager = "searchCacheManager",
            key = "'authorImpact:' + #authorName.trim().toLowerCase()",
            unless = "#result == null")
    public AuthorImpactReportResponse getAuthorImpactReport(String authorName, String lang) {
        String trimmed = authorName.trim();
        if (trimmed.isEmpty()) {
            return buildEmptyAuthorReport(authorName, lang);
        }

        log.info("Generating author impact report for: '{}'", trimmed);

        Integer totalPapers = null;
        Integer hIndex = null;
        String affiliation = null;
        UUID authorId = null;

        // Step 1: Try local DB first for author
        List<Author> localAuthors = authorRepository.searchByName(trimmed, PageRequest.of(0, 1));
        if (!localAuthors.isEmpty()) {
            Author localAuthor = localAuthors.get(0);
            authorId = localAuthor.getAuthorId();
            totalPapers = (int) paperAuthorRepository.countByAuthor_AuthorId(authorId);
            hIndex = localAuthor.getHIndex();
            affiliation = localAuthor.getAffiliation();
            log.info("Author found locally: id={}, name={}, hIndex={}", authorId, localAuthor.getFullName(), hIndex);
        }

        // Step 2: Try OpenAlex for richer data (especially h-index and affiliation)
        AuthorQuickStatsResponse openAlexStats = null;
        try {
            openAlexStats = authorQuickStatsService.searchAuthor(trimmed);
            log.info("OpenAlex author data found for '{}': works={}, hIndex={}",
                    trimmed,
                    openAlexStats.getTotalPapers(),
                    openAlexStats.getHIndex());
            // Prefer OpenAlex data when available (more comprehensive)
            if (totalPapers == null || openAlexStats.getTotalPapers() > totalPapers) {
                totalPapers = openAlexStats.getTotalPapers();
            }
            if (hIndex == null || hIndex == 0
                    || (openAlexStats.getHIndex() != null && openAlexStats.getHIndex() > hIndex)) {
                hIndex = openAlexStats.getHIndex();
            }
            if (affiliation == null || affiliation.isBlank()) {
                affiliation = openAlexStats.getCurrentAffiliation();
            }
        } catch (AppException e) {
            log.info("OpenAlex author lookup failed for '{}': {} — using local data only", trimmed, e.getMessage());
        } catch (Exception e) {
            log.warn("Unexpected error in OpenAlex author lookup for '{}': {}", trimmed, e.getMessage());
        }

        // Step 3: If author not found anywhere
        if (totalPapers == null && authorId == null) {
            log.info("No author found for '{}'", trimmed);
            throw new AppException(ErrorCode.AUTHOR_NOT_FOUND);
        }

        // Step 4: Determine activity status
        // Falls back to OpenAlex timeline check if no local author ID
        String status = determineAuthorStatus(authorId, trimmed);

        // Step 5: Find top research field
        String topField = findTopFieldForAuthor(authorId);

        // Step 6: Find top collaborators (from local DB if we have authorId, else from OpenAlex)
        List<AuthorImpactReportResponse.Collaborator> topCollaborators = findTopCollaborators(authorId, trimmed);

        // Step 7: Generate insight text
        String insight = generateAuthorInsight(trimmed, hIndex, topField, status, lang);

        String reportTitle = isEnglish(lang)
                ? "Academic Profile: " + trimmed
                : "Hồ sơ năng lực học thuật: " + trimmed;

        log.info("Author impact report for '{}': papers={}, hIndex={}, status={}, field={}",
                trimmed, totalPapers, hIndex, status, topField);

        return AuthorImpactReportResponse.builder()
                .reportTitle(reportTitle)
                .authorName(trimmed)
                .affiliation(affiliation)
                .totalPapers(totalPapers)
                .hIndex(hIndex)
                .status(status)
                .insight(insight)
                .topField(topField)
                .topCollaborators(topCollaborators)
                .build();
    }

    // ============================================
    //  JOURNAL QUALITY REPORT
    // ============================================

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "search:report", cacheManager = "searchCacheManager",
            key = "'journalQuality:' + #journalName.trim().toLowerCase()",
            unless = "#result == null || #result.totalPapers == 0")
    public JournalQualityReportResponse getJournalQualityReport(String journalName, String lang) {
        String trimmed = journalName.trim();
        if (trimmed.isEmpty()) {
            return buildEmptyJournalReport(journalName, lang);
        }

        log.info("Generating journal quality report for: '{}'", trimmed);

        // Step 1: Get journal quick stats (quartile, impact factor, total papers, citations)
        JournalQuickStatsResponse stats;
        try {
            stats = journalQuickStatsService.getStats(trimmed);
        } catch (Exception e) {
            log.warn("Journal quick stats failed for '{}': {}", trimmed, e.getMessage());
            return buildEmptyJournalReport(trimmed, lang);
        }

        if (stats.getTotalPapers() == null || stats.getTotalPapers() == 0) {
            log.info("No papers found for journal '{}'", trimmed);
            return buildEmptyJournalReport(trimmed, lang);
        }

        // Step 2: Find the journal entity for recent keyword queries
        List<Journal> journals = journalRepository.searchByName(trimmed, PageRequest.of(0, 1));
        UUID journalId = null;
        if (!journals.isEmpty()) {
            journalId = journals.get(0).getJournalId();
        }

        // Step 3: Recent keywords (last 2 years) — editorial "taste"
        List<String> recentKeywords = getRecentJournalKeywords(journalId);

        // Step 4: Composite score (impact factor or calculated cite score)
        Double score = stats.getImpactFactor() != null
                ? stats.getImpactFactor().doubleValue()
                : stats.getCalculatedCiteScore();

        // Step 5: Generate taste text
        String taste = generateJournalTaste(recentKeywords, lang);

        String insight = generateJournalInsight(stats.getQuartile(), stats.getJournalName(), stats.getImpactFactor(), lang);

        String reportTitle = isEnglish(lang)
                ? "Journal Quality: " + stats.getJournalName()
                : "Đánh giá chất lượng tạp chí: " + stats.getJournalName();

        log.info("Journal quality report for '{}': Q={}, IF={}, papers={}, citations={}",
                stats.getJournalName(), stats.getQuartile(), stats.getImpactFactor(),
                stats.getTotalPapers(), stats.getTotalCitations());

        return JournalQualityReportResponse.builder()
                .reportTitle(reportTitle)
                .journalName(stats.getJournalName())
                .issn(stats.getIssn())
                .publisher(stats.getPublisher())
                .quartile(stats.getQuartile())
                .impactFactor(stats.getImpactFactor())
                .score(score)
                .taste(taste)
                .insight(insight)
                .totalPapers(stats.getTotalPapers())
                .totalCitations(stats.getTotalCitations())
                .topKeywords(recentKeywords)
                .build();
    }

    // ============================================
    //  PRIVATE HELPERS — Keyword Trend
    // ============================================

    private KeywordTrendReportResponse buildEmptyKeywordReport(String keyword, String lang) {
        boolean en = isEnglish(lang);
        return KeywordTrendReportResponse.builder()
                .keyword(keyword)
                .reportTitle(en ? "Trend Report: " + keyword : "Báo cáo xu hướng: " + keyword)
                .summary(KeywordTrendReportResponse.Summary.builder()
                        .totalPublications(0L)
                        .totalCitations(0L)
                        .build())
                .publicationTrend(List.of())
                .citationTrend(List.of())
                .coOccurringKeywords(List.of())
                .topJournals(List.of())
                .insight(en
                        ? "Not enough data about \"" + keyword + "\" in the system. Please try a different keyword or wait for data to sync."
                        : "Chưa có đủ dữ liệu về chủ đề \"" + keyword
                                + "\" trong hệ thống. Vui lòng thử tìm kiếm với từ khóa khác hoặc đợi dữ liệu được đồng bộ.")
                .build();
    }

    // ── Private helpers — using local Neo4j + SQL data only ──

    private KeywordTrendReportResponse.Summary buildSummary(String keyword, List<UUID> paperIds, long totalPapers) {
        long totalCitations = researchPaperRepository.sumCitationCountByKeyword(keyword);
        Integer peakYear = null;
        try {
            List<Short> peakYears = researchPaperRepository.findPeakYearByPaperIds(
                    paperIds, PageRequest.of(0, 1));
            if (!peakYears.isEmpty() && peakYears.get(0) != null) {
                peakYear = peakYears.get(0).intValue();
            }
        } catch (Exception e) {
            log.warn("Failed to find peak year for keyword '{}': {}", keyword, e.getMessage());
        }
        KeywordTrendReportResponse.TopJournalInfo topJournal = null;
        try {
            List<Object[]> topJournals = researchPaperRepository.findTopJournalsByPaperIds(
                    paperIds, PageRequest.of(0, 1));
            if (!topJournals.isEmpty()) {
                Object[] row = topJournals.get(0);
                topJournal = KeywordTrendReportResponse.TopJournalInfo.builder()
                        .name((String) row[0])
                        .paperCount(((Number) row[4]).intValue())
                        .build();
            }
        } catch (Exception e) {
            log.warn("Failed to find top journal for keyword '{}': {}", keyword, e.getMessage());
        }
        return KeywordTrendReportResponse.Summary.builder()
                .totalPublications(totalPapers)
                .totalCitations(totalCitations)
                .peakYear(peakYear)
                .topJournal(topJournal)
                .build();
    }

    private List<KeywordTrendReportResponse.TrendPoint> buildPublicationTrend(List<UUID> paperIds, short startYear) {
        try {
            List<Object[]> rows = researchPaperRepository.countPapersByYearForIds(paperIds, startYear);
            return rows.stream()
                    .map(row -> KeywordTrendReportResponse.TrendPoint.builder()
                            .year(((Short) row[0]).intValue())
                            .count(((Number) row[1]).longValue())
                            .build())
                    .toList();
        } catch (Exception e) {
            log.warn("Failed to build publication trend: {}", e.getMessage());
            return List.of();
        }
    }

    private List<KeywordTrendReportResponse.TrendPoint> buildCitationTrend(List<UUID> paperIds, short startYear) {
        try {
            List<Object[]> rows = researchPaperRepository.sumCitationsByYearForIds(paperIds, startYear);
            return rows.stream()
                    .map(row -> KeywordTrendReportResponse.TrendPoint.builder()
                            .year(((Short) row[0]).intValue())
                            .count(((Number) row[1]).longValue())
                            .build())
                    .toList();
        } catch (Exception e) {
            log.warn("Failed to build citation trend: {}", e.getMessage());
            return List.of();
        }
    }

    private List<KeywordTrendReportResponse.CoOccurringKeyword> buildCoOccurringKeywords(
            String keyword, int startYear, short thisYear, short lastYear) {
        try {
            List<Map<String, Object>> coOccurring = graphService.getCooccurringKeywords(
                    keyword.toLowerCase(), startYear, thisYear, lastYear, 8);
            return coOccurring.stream()
                    .map(row -> KeywordTrendReportResponse.CoOccurringKeyword.builder()
                            .keyword(row.get("originalKeyword").toString())
                            .count((Long) row.get("totalCount"))
                            .build())
                    .toList();
        } catch (Exception e) {
            log.warn("Failed to get co-occurring keywords for '{}': {}", keyword, e.getMessage());
            return List.of();
        }
    }

    private List<KeywordTrendReportResponse.TopJournal> buildTopJournals(List<UUID> paperIds) {
        try {
            List<Object[]> rows = researchPaperRepository.findTopJournalsByPaperIds(
                    paperIds, PageRequest.of(0, 6));
            return rows.stream()
                    .map(row -> KeywordTrendReportResponse.TopJournal.builder()
                            .name((String) row[0])
                            .count(((Number) row[4]).longValue())
                            .build())
                    .toList();
        } catch (Exception e) {
            log.warn("Failed to build top journals: {}", e.getMessage());
            return List.of();
        }
    }

    private String generateKeywordInsight(String keyword, KeywordTrendReportResponse.Summary summary, String lang) {
        boolean en = isEnglish(lang);
        long totalPapers = summary.getTotalPublications() != null ? summary.getTotalPublications() : 0;
        long totalCitations = summary.getTotalCitations() != null ? summary.getTotalCitations() : 0;
        Integer peakYear = summary.getPeakYear();

        if (totalPapers == 0) {
            return en
                    ? "Topic \"" + keyword + "\" does not have enough data in the system for accurate analysis."
                    : "Chủ đề \"" + keyword
                            + "\" chưa có đủ dữ liệu trong hệ thống để đưa ra nhận định chính xác.";
        }

        StringBuilder insight = new StringBuilder();
        if (en) {
            insight.append("Topic \"").append(keyword).append("\" has a total of ")
                    .append(String.format("%,d", totalPapers)).append(" papers");
        } else {
            insight.append("Chủ đề \"").append(keyword).append("\" có tổng cộng ")
                    .append(String.format("%,d", totalPapers)).append(" bài báo");
        }

        if (totalCitations > 0) {
            insight.append(en ? " with " : " với ")
                    .append(String.format("%,d", totalCitations))
                    .append(en ? " citations" : " lượt trích dẫn");
        }

        if (peakYear != null) {
            insight.append(en ? ", peaking in " : ", đạt đỉnh cao vào năm ").append(peakYear);
        }

        insight.append(". ");

        if (summary.getTopJournal() != null && summary.getTopJournal().getName() != null) {
            if (en) {
                insight.append("The most publishing journal is ")
                        .append(summary.getTopJournal().getName())
                        .append(" with ").append(summary.getTopJournal().getPaperCount())
                        .append(" papers. ");
            } else {
                insight.append("Tạp chí xuất bản nhiều nhất là ")
                        .append(summary.getTopJournal().getName())
                        .append(" với ").append(summary.getTopJournal().getPaperCount())
                        .append(" bài báo. ");
            }
        }

        if (totalPapers > 5000) {
            insight.append(en
                    ? "This is a large research field with abundant publications, attracting strong interest from the academic community."
                    : "Đây là một lĩnh vực nghiên cứu lớn với lượng xuất bản dồi dào, "
                            + "thu hút sự quan tâm mạnh mẽ từ cộng đồng học thuật.");
        } else if (totalPapers > 1000) {
            insight.append(en
                    ? "This is a growing research field with stable publication volume."
                    : "Đây là một lĩnh vực nghiên cứu đang phát triển với lượng xuất bản ổn định.");
        } else {
            insight.append(en
                    ? "This is a specialized research field with potential for further exploration."
                    : "Đây là một lĩnh vực nghiên cứu chuyên sâu, có thể còn nhiều tiềm năng khai phá.");
        }

        return insight.toString();
    }

    // ============================================
    //  PRIVATE HELPERS — Author Impact
    // ============================================

    private AuthorImpactReportResponse buildEmptyAuthorReport(String authorName, String lang) {
        boolean en = isEnglish(lang);
        return AuthorImpactReportResponse.builder()
                .reportTitle(en ? "Academic Profile: " + authorName : "Hồ sơ năng lực học thuật: " + authorName)
                .authorName(authorName)
                .totalPapers(0)
                .hIndex(0)
                .status("Không có dữ liệu")
                .insight(en
                        ? "No information found about author \"" + authorName + "\" in the system."
                        : "Chưa tìm thấy thông tin về tác giả \"" + authorName + "\" trong hệ thống.")
                .topField(null)
                .topCollaborators(List.of())
                .build();
    }

    private String determineAuthorStatus(UUID authorId, String authorName) {
        if (authorId != null) {
            try {
                short startYear = (short) (currentYear() - AUTHOR_ACTIVITY_YEAR_WINDOW + 1);
                long recentPapers = researchPaperRepository.countRecentPapersByAuthorId(authorId, startYear);
                return recentPapers > 0 ? "Đang sung sức" : "Đã dừng nghiên cứu";
            } catch (Exception e) {
                log.warn("Failed to determine author status for {}: {}", authorId, e.getMessage());
            }
        }

        // Fallback: try OpenAlex timeline to check recent activity
        try {
            var timeline = authorQuickStatsService.getTimeline(authorName);
            if (timeline.getTimeline() != null && !timeline.getTimeline().isEmpty()) {
                short thisYear = currentYear();
                short recentCutoff = (short) (thisYear - AUTHOR_ACTIVITY_YEAR_WINDOW + 1);
                boolean hasRecent = timeline.getTimeline().stream()
                        .anyMatch(y -> y.getYear() >= recentCutoff && y.getWorksCount() > 0);
                return hasRecent ? "Đang sung sức" : "Đã dừng nghiên cứu";
            }
        } catch (Exception e) {
            log.warn("Failed to get OpenAlex timeline for '{}': {}", authorName, e.getMessage());
        }

        return "Không có dữ liệu";
    }

    private String findTopFieldForAuthor(UUID authorId) {
        if (authorId == null) {
            return null;
        }
        try {
            List<UUID> fieldIds = paperAuthorRepository.findTopFieldIdsByAuthorId(authorId);
            if (!fieldIds.isEmpty()) {
                Optional<ResearchField> field = researchFieldRepository.findById(fieldIds.get(0));
                return field.map(ResearchField::getFieldName).orElse(null);
            }
        } catch (Exception e) {
            log.warn("Failed to find top field for author {}: {}", authorId, e.getMessage());
        }
        return null;
    }

    private List<AuthorImpactReportResponse.Collaborator> findTopCollaborators(UUID authorId, String authorName) {
        if (authorId == null) {
            // Try OpenAlex for co-authors
            try {
                var coAuthors = authorQuickStatsService.getCoAuthors(authorName);
                if (coAuthors.getCoAuthors() != null) {
                    return coAuthors.getCoAuthors().stream()
                            .limit(5)
                            .map(ca -> AuthorImpactReportResponse.Collaborator.builder()
                                    .name(ca.getName())
                                    .affiliation(ca.getLastInstitution())
                                    .collaborationCount(ca.getCollaborationCount())
                                    .build())
                            .toList();
                }
            } catch (Exception e) {
                log.warn("Failed to get OpenAlex co-authors for '{}': {}", authorName, e.getMessage());
            }
            return List.of();
        }

        try {
            List<Object[]> rows = paperAuthorRepository.findCoAuthorsByAuthorId(
                    authorId, PageRequest.of(0, 5));
            return rows.stream()
                    .map(row -> AuthorImpactReportResponse.Collaborator.builder()
                            .name((String) row[0])
                            .affiliation((String) row[1])
                            .collaborationCount(((Number) row[2]).intValue())
                            .build())
                    .toList();
        } catch (Exception e) {
            log.warn("Failed to get co-authors for author {}: {}", authorId, e.getMessage());
            return List.of();
        }
    }

    private String generateAuthorInsight(String authorName, Integer hIndex, String topField, String status, String lang) {
        boolean en = isEnglish(lang);

        if (hIndex == null || hIndex == 0) {
            if (topField != null && !topField.isBlank()) {
                return en
                        ? "Author " + authorName + " works in the field of " + topField
                                + ". Citation data is insufficient to evaluate h-index."
                        : "Tác giả " + authorName + " hoạt động trong lĩnh vực " + topField
                                + ". Dữ liệu trích dẫn chưa đủ để đánh giá chỉ số h-index.";
            }
            return en
                    ? "Author " + authorName + " does not have enough citation data in the system for a full evaluation."
                    : "Tác giả " + authorName + " chưa có đủ dữ liệu trích dẫn trong hệ thống để đưa ra đánh giá đầy đủ.";
        }

        StringBuilder insight = new StringBuilder();
        if (en) {
            insight.append("The author has an h-index of ").append(hIndex);
        } else {
            insight.append("Tác giả có chỉ số h-index đạt ").append(hIndex);
        }

        if (topField != null && !topField.isBlank()) {
            if (en) {
                insight.append(", establishing expert status in ").append(topField).append(". ");
            } else {
                insight.append(", khẳng định vị thế chuyên gia trong lĩnh vực ").append(topField).append(". ");
            }
        } else {
            insight.append(en ? ", demonstrating significant research capability. " : ", thể hiện năng lực nghiên cứu đáng kể. ");
        }

        if ("Đang sung sức".equals(status)) {
            insight.append(en
                    ? "The author is still actively publishing research in recent years."
                    : "Tác giả vẫn đang tích cực công bố nghiên cứu trong những năm gần đây.");
        } else if ("Đã dừng nghiên cứu".equals(status)) {
            insight.append(en
                    ? "However, the author appears to have reduced or stopped publishing in the last 3 years."
                    : "Tuy nhiên, tác giả dường như đã giảm hoặc dừng công bố trong 3 năm gần đây.");
        }

        return insight.toString();
    }

    // ============================================
    //  PRIVATE HELPERS — Journal Quality
    // ============================================

    private JournalQualityReportResponse buildEmptyJournalReport(String journalName, String lang) {
        boolean en = isEnglish(lang);
        return JournalQualityReportResponse.builder()
                .reportTitle(en ? "Journal Quality: " + journalName : "Đánh giá chất lượng tạp chí: " + journalName)
                .journalName(journalName)
                .quartile(null)
                .impactFactor(null)
                .score(null)
                .taste(en
                        ? "No data available on the publication trends of this journal."
                        : "Chưa có dữ liệu về xu hướng đăng tải của tạp chí này.")
                .insight(en
                        ? "No data found for journal \"" + journalName + "\" in the system."
                        : "Chưa tìm thấy dữ liệu về tạp chí \"" + journalName + "\" trong hệ thống.")
                .totalPapers(0L)
                .totalCitations(0L)
                .topKeywords(List.of())
                .build();
    }

    private List<String> getRecentJournalKeywords(UUID journalId) {
        if (journalId == null) {
            return List.of();
        }
        try {
            short startYear = (short) (currentYear() - JOURNAL_RECENT_YEAR_WINDOW + 1);
            List<Object[]> rows = researchPaperRepository.findTopKeywordsByJournalIdRecent(
                    journalId, startYear, PageRequest.of(0, 5));
            return rows.stream()
                    .map(row -> (String) row[0])
                    .toList();
        } catch (Exception e) {
            log.warn("Failed to get recent keywords for journal {}: {}", journalId, e.getMessage());
            return List.of();
        }
    }

    private String generateJournalTaste(List<String> recentKeywords, String lang) {
        boolean en = isEnglish(lang);
        if (recentKeywords == null || recentKeywords.isEmpty()) {
            return en
                    ? "Not enough data to determine recent publication trends for this journal."
                    : "Chưa có đủ dữ liệu để xác định xu hướng đăng tải gần đây của tạp chí này.";
        }
        if (recentKeywords.size() == 1) {
            return en
                    ? "The journal prioritizes publishing research on " + recentKeywords.get(0) + " in the last 2 years."
                    : "Tạp chí đang ưu tiên đăng tải các nghiên cứu về "
                            + recentKeywords.get(0) + " trong 2 năm gần đây.";
        }
        String keywordA = recentKeywords.get(0);
        String keywordB = recentKeywords.get(1);
        return en
                ? "The journal prioritizes publishing research on " + keywordA + ", " + keywordB + " in the last 2 years."
                : "Tạp chí đang ưu tiên đăng tải các nghiên cứu về "
                        + keywordA + ", " + keywordB + " trong 2 năm gần đây.";
    }

    private String generateJournalInsight(String quartile, String journalName, java.math.BigDecimal impactFactor, String lang) {
        boolean en = isEnglish(lang);

        String ifStr = impactFactor != null ? " (IF=" + impactFactor + ")" : "";

        if (quartile == null) {
            return en
                    ? "Journal \"" + journalName + "\" has no quartile ranking in the system."
                    : "Tạp chí \"" + journalName + "\" chưa có xếp hạng quartile trong hệ thống.";
        }

        return switch (quartile.toUpperCase()) {
            case "Q1" -> en
                    ? "This is a Q1 journal" + ifStr + ", suitable for in-depth, groundbreaking research. "
                            + "Acceptance rates are typically low, requiring excellent research quality."
                    : "Đây là tạp chí nhóm Q1" + ifStr
                            + ", phù hợp cho các nghiên cứu chuyên sâu, có tính đột phá cao. "
                            + "Tỷ lệ chấp nhận thường thấp, đòi hỏi chất lượng nghiên cứu xuất sắc.";
            case "Q2" -> en
                    ? "This is a Q2 journal" + ifStr + ", with good reputation and suitable for high-quality research. "
                            + "Good balance between prestige and acceptance likelihood."
                    : "Đây là tạp chí nhóm Q2" + ifStr
                            + ", có uy tín tốt và phù hợp cho các nghiên cứu chất lượng cao. "
                            + "Cân bằng tốt giữa độ uy tín và khả năng được chấp nhận.";
            case "Q3" -> en
                    ? "This is a Q3 journal" + ifStr + ", suitable for niche or early-stage research. "
                            + "Acceptance rates are higher compared to Q1-Q2."
                    : "Đây là tạp chí nhóm Q3" + ifStr
                            + ", phù hợp cho các nghiên cứu ở mức chuyên ngành hẹp hoặc mới bắt đầu. "
                            + "Tỷ lệ chấp nhận cao hơn so với nhóm Q1-Q2.";
            case "Q4" -> en
                    ? "This is a Q4 journal" + ifStr + ", suitable for preliminary research or short reports. "
                            + "Consider the prestige level carefully when choosing to publish."
                    : "Đây là tạp chí nhóm Q4" + ifStr
                            + ", phù hợp cho các nghiên cứu bước đầu hoặc báo cáo ngắn. "
                            + "Cần cân nhắc kỹ về độ uy tín khi lựa chọn xuất bản.";
            default -> en
                    ? "Journal \"" + journalName + "\" has ranking " + quartile + ifStr + "."
                    : "Tạp chí \"" + journalName + "\" có xếp hạng " + quartile + ifStr + ".";
        };
    }

    // ============================================
    //  PERSISTENCE HELPERS
    // ============================================

    /**
     * Persists the computed keyword trend report to the database as JSON.
     * Upserts by normalized keyword — inserts if new, updates if exists.
     * Failures are logged but never thrown (non-blocking for the API response).
     */
    private void saveKeywordTrendToDb(String keyword, KeywordTrendReportResponse report) {
        try {
            String normalized = keyword.trim().toLowerCase();
            String json = objectMapper.writeValueAsString(report);

            KeywordTrendCache cache = keywordTrendCacheRepository
                    .findByNormalizedKeyword(normalized)
                    .orElse(KeywordTrendCache.builder()
                            .keyword(keyword.trim())
                            .normalizedKeyword(normalized)
                            .build());

            cache.setReportData(json);
            keywordTrendCacheRepository.save(cache);

            log.debug("Persisted keyword trend report for '{}' to DB ({} chars)", keyword, json.length());
        } catch (Exception e) {
            log.warn("Failed to persist keyword trend report for '{}': {}", keyword, e.getMessage());
        }
    }

    // ============================================
    //  CACHED REPORT RETRIEVAL
    // ============================================

    @Override
    public KeywordTrendReportResponse getCachedKeywordTrendReport(String keyword, String lang) {
        String normalized = keyword.trim().toLowerCase();
        log.info("Looking up cached keyword trend report for '{}'", keyword);

        Optional<KeywordTrendCache> cached = keywordTrendCacheRepository
                .findByNormalizedKeyword(normalized);

        if (cached.isPresent()) {
            try {
                KeywordTrendReportResponse report = objectMapper.readValue(
                        cached.get().getReportData(), KeywordTrendReportResponse.class);
                log.info("Returning cached report for '{}' (from {})",
                        keyword, cached.get().getUpdatedAt());
                return report;
            } catch (Exception e) {
                log.warn("Failed to deserialize cached report for '{}', will regenerate: {}",
                        keyword, e.getMessage());
            }
        }

        // Cache miss or deserialization failure — generate fresh
        log.info("Cache miss for '{}', generating fresh report", keyword);
        return getKeywordTrendReport(keyword, null, null, lang);
    }

    @Override
    public List<KeywordTrendHistoryItem> getKeywordTrendHistory() {
        log.info("Fetching all keyword trend report history");

        List<KeywordTrendCache> cached = keywordTrendCacheRepository
                .findAllByOrderByUpdatedAtDesc();

        return cached.stream()
                .map(c -> KeywordTrendHistoryItem.builder()
                        .keyword(c.getKeyword())
                        .createdAt(c.getCreatedAt())
                        .updatedAt(c.getUpdatedAt())
                        .build())
                .toList();
    }

    @Override
    @Transactional
    public void deleteCachedKeywordTrendReport(String keyword) {
        String normalized = keyword.trim().toLowerCase();
        log.info("Deleting cached keyword trend report for '{}'", keyword);

        keywordTrendCacheRepository.deleteByNormalizedKeyword(normalized);
    }

    // ============================================
    //  MATH HELPERS
    // ============================================

    private short currentYear() {
        return (short) Year.now().getValue();
    }
}
