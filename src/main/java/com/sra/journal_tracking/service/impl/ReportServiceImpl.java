package com.sra.journal_tracking.service.impl;

import com.sra.journal_tracking.dto.author.AuthorQuickStatsResponse;
import com.sra.journal_tracking.dto.journal.JournalQuickStatsResponse;
import com.sra.journal_tracking.dto.report.AuthorImpactReportResponse;
import com.sra.journal_tracking.dto.report.JournalQualityReportResponse;
import com.sra.journal_tracking.dto.report.KeywordTrendReportResponse;
import com.sra.journal_tracking.entity.jpa.Author;
import com.sra.journal_tracking.entity.jpa.Journal;
import com.sra.journal_tracking.entity.jpa.ResearchField;
import com.sra.journal_tracking.exception.AppException;
import com.sra.journal_tracking.exception.ErrorCode;
import com.sra.journal_tracking.repository.jpa.AuthorRepository;
import com.sra.journal_tracking.repository.jpa.JournalRepository;
import com.sra.journal_tracking.repository.jpa.PaperAuthorRepository;
import com.sra.journal_tracking.repository.jpa.ResearchFieldRepository;
import com.sra.journal_tracking.repository.jpa.ResearchPaperRepository;
import com.sra.journal_tracking.service.AuthorQuickStatsService;
import com.sra.journal_tracking.service.GraphService;
import com.sra.journal_tracking.service.JournalQuickStatsService;
import com.sra.journal_tracking.service.ReportService;
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

    private static final double BOOMING_THRESHOLD = 20.0;
    private static final int KEYWORD_TREND_YEAR_WINDOW = 5;
    private static final int JOURNAL_RECENT_YEAR_WINDOW = 2;
    private static final int AUTHOR_ACTIVITY_YEAR_WINDOW = 3;

    private final GraphService graphService;
    private final AuthorQuickStatsService authorQuickStatsService;
    private final JournalQuickStatsService journalQuickStatsService;
    private final ResearchPaperRepository researchPaperRepository;
    private final AuthorRepository authorRepository;
    private final JournalRepository journalRepository;
    private final PaperAuthorRepository paperAuthorRepository;
    private final ResearchFieldRepository researchFieldRepository;

    // ============================================
    //  KEYWORD TREND REPORT
    // ============================================

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "search:report", cacheManager = "searchCacheManager",
            key = "'keywordTrend:' + #keyword.trim().toLowerCase()",
            unless = "#result == null || #result.totalPapers == 0")
    public KeywordTrendReportResponse getKeywordTrendReport(String keyword) {
        String trimmed = keyword.trim();
        if (trimmed.isEmpty()) {
            return buildEmptyKeywordReport(keyword);
        }

        log.info("Generating keyword trend report for: '{}'", trimmed);

        // Step 1: Total papers from Neo4j
        long totalPapers = graphService.countPapersByKeyword(trimmed);
        if (totalPapers == 0) {
            log.info("No papers found for keyword '{}'", trimmed);
            return buildEmptyKeywordReport(trimmed);
        }

        // Step 2: Get paper IDs from Neo4j
        List<String> paperIdStrings = graphService.getAllPaperIdsByKeyword(trimmed);
        List<UUID> paperIds = paperIdStrings.stream()
                .map(UUID::fromString)
                .toList();

        if (paperIds.isEmpty()) {
            return buildEmptyKeywordReport(trimmed);
        }

        // Step 3: YoY growth rate (this year vs last year)
        short thisYear = currentYear();
        short lastYear = (short) (thisYear - 1);
        long papersThisYear = researchPaperRepository.countByPaperIdsAndPubYear(paperIds, thisYear);
        long papersLastYear = researchPaperRepository.countByPaperIdsAndPubYear(paperIds, lastYear);

        Double yoyGrowthRate = null;
        if (papersLastYear > 0) {
            yoyGrowthRate = roundToOneDecimal(((double) (papersThisYear - papersLastYear) / papersLastYear) * 100.0);
        } else if (papersThisYear > 0) {
            yoyGrowthRate = 100.0;
        }

        // Step 4: Determine status
        String status = determineKeywordStatus(yoyGrowthRate);

        // Step 5: Yearly breakdown (last 5 years)
        short startYear = (short) (thisYear - KEYWORD_TREND_YEAR_WINDOW + 1);
        List<KeywordTrendReportResponse.YearlyDataPoint> yearlyBreakdown = buildYearlyBreakdown(paperIds, startYear);

        // Step 6: Top 3 related keywords from Neo4j co-occurrence
        List<String> topRelatedKeywords = getTopRelatedKeywords(trimmed, thisYear, lastYear);

        // Step 7: Generate insight text
        String insight = generateKeywordInsight(trimmed, yoyGrowthRate, status, totalPapers);

        // Step 8: Build report title
        String reportTitle = "Báo cáo phân tích chủ đề: " + trimmed;

        log.info("Keyword trend report for '{}': papers={}, yoy={}%, status={}",
                trimmed, totalPapers, yoyGrowthRate, status);

        return KeywordTrendReportResponse.builder()
                .reportTitle(reportTitle)
                .keyword(trimmed)
                .totalPapers(totalPapers)
                .yoyGrowthRate(yoyGrowthRate)
                .status(status)
                .insight(insight)
                .topRelatedKeywords(topRelatedKeywords)
                .yearlyBreakdown(yearlyBreakdown)
                .build();
    }

    // ============================================
    //  AUTHOR IMPACT REPORT
    // ============================================

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "search:report", cacheManager = "searchCacheManager",
            key = "'authorImpact:' + #authorName.trim().toLowerCase()",
            unless = "#result == null")
    public AuthorImpactReportResponse getAuthorImpactReport(String authorName) {
        String trimmed = authorName.trim();
        if (trimmed.isEmpty()) {
            return buildEmptyAuthorReport(authorName);
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
        String insight = generateAuthorInsight(trimmed, hIndex, topField, status);

        // Step 8: Build report title
        String reportTitle = "Hồ sơ năng lực học thuật: " + trimmed;

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
    public JournalQualityReportResponse getJournalQualityReport(String journalName) {
        String trimmed = journalName.trim();
        if (trimmed.isEmpty()) {
            return buildEmptyJournalReport(journalName);
        }

        log.info("Generating journal quality report for: '{}'", trimmed);

        // Step 1: Get journal quick stats (quartile, impact factor, total papers, citations)
        JournalQuickStatsResponse stats;
        try {
            stats = journalQuickStatsService.getStats(trimmed);
        } catch (Exception e) {
            log.warn("Journal quick stats failed for '{}': {}", trimmed, e.getMessage());
            return buildEmptyJournalReport(trimmed);
        }

        if (stats.getTotalPapers() == null || stats.getTotalPapers() == 0) {
            log.info("No papers found for journal '{}'", trimmed);
            return buildEmptyJournalReport(trimmed);
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
        String taste = generateJournalTaste(recentKeywords);

        // Step 6: Generate insight text
        String insight = generateJournalInsight(stats.getQuartile(), stats.getJournalName(), stats.getImpactFactor());

        // Step 7: Build report title
        String reportTitle = "Đánh giá chất lượng tạp chí: " + stats.getJournalName();

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

    private KeywordTrendReportResponse buildEmptyKeywordReport(String keyword) {
        return KeywordTrendReportResponse.builder()
                .reportTitle("Báo cáo phân tích chủ đề: " + keyword)
                .keyword(keyword)
                .totalPapers(0L)
                .yoyGrowthRate(null)
                .status("Không có dữ liệu")
                .insight("Chưa có đủ dữ liệu về chủ đề \"" + keyword + "\" trong hệ thống. Vui lòng thử tìm kiếm với từ khóa khác hoặc đợi dữ liệu được đồng bộ.")
                .topRelatedKeywords(List.of())
                .yearlyBreakdown(List.of())
                .build();
    }

    private String determineKeywordStatus(Double yoyGrowthRate) {
        if (yoyGrowthRate == null) {
            return "Không có dữ liệu";
        }
        if (yoyGrowthRate > BOOMING_THRESHOLD) {
            return "Đang bùng nổ";
        }
        if (yoyGrowthRate >= 0) {
            return "Ổn định";
        }
        return "Bão hòa";
    }

    private List<KeywordTrendReportResponse.YearlyDataPoint> buildYearlyBreakdown(List<UUID> paperIds, short startYear) {
        try {
            List<Object[]> rows = researchPaperRepository.countPapersByYearForIds(paperIds, startYear);
            return rows.stream()
                    .map(row -> KeywordTrendReportResponse.YearlyDataPoint.builder()
                            .year(((Short) row[0]).intValue())
                            .paperCount(((Number) row[1]).longValue())
                            .build())
                    .toList();
        } catch (Exception e) {
            log.warn("Failed to build yearly breakdown: {}", e.getMessage());
            return List.of();
        }
    }

    private List<String> getTopRelatedKeywords(String keyword, short thisYear, short lastYear) {
        try {
            int startYear = thisYear - KEYWORD_TREND_YEAR_WINDOW + 1;
            List<Map<String, Object>> coOccurring = graphService.getCooccurringKeywords(
                    keyword.toLowerCase(), startYear, thisYear, lastYear, 10);
            return coOccurring.stream()
                    .map(row -> row.get("originalKeyword").toString())
                    .limit(3)
                    .toList();
        } catch (Exception e) {
            log.warn("Failed to get related keywords for '{}': {}", keyword, e.getMessage());
            return List.of();
        }
    }

    private String generateKeywordInsight(String keyword, Double yoyGrowthRate, String status, long totalPapers) {
        if (yoyGrowthRate == null) {
            return "Chủ đề \"" + keyword + "\" hiện có " + totalPapers
                    + " bài báo trong hệ thống. Dữ liệu tăng trưởng chưa đủ để đưa ra nhận định chính xác.";
        }

        String growthDesc = Math.abs(yoyGrowthRate) >= 10
                ? String.format("%.1f%%", yoyGrowthRate)
                : String.format("%.1f%%", yoyGrowthRate);

        return switch (status) {
            case "Đang bùng nổ" -> "Chủ đề này có tốc độ tăng trưởng " + growthDesc
                    + ", cao hơn mức trung bình của ngành, cho thấy đây là một ngách tiềm năng cao. "
                    + "Các nhà nghiên cứu đang tập trung mạnh vào lĩnh vực này.";
            case "Bão hòa" -> "Chủ đề này có tốc độ tăng trưởng " + growthDesc
                    + ", thấp hơn mức trung bình của ngành. "
                    + "Lĩnh vực này có thể đã bão hòa hoặc đang chuyển hướng sang các chủ đề liên quan.";
            default -> "Chủ đề này có tốc độ tăng trưởng " + growthDesc
                    + ", ở mức ổn định. "
                    + "Đây là lĩnh vực nghiên cứu vững chắc với lượng xuất bản duy trì đều đặn.";
        };
    }

    // ============================================
    //  PRIVATE HELPERS — Author Impact
    // ============================================

    private AuthorImpactReportResponse buildEmptyAuthorReport(String authorName) {
        return AuthorImpactReportResponse.builder()
                .reportTitle("Hồ sơ năng lực học thuật: " + authorName)
                .authorName(authorName)
                .totalPapers(0)
                .hIndex(0)
                .status("Không có dữ liệu")
                .insight("Chưa tìm thấy thông tin về tác giả \"" + authorName + "\" trong hệ thống.")
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

    private String generateAuthorInsight(String authorName, Integer hIndex, String topField, String status) {
        if (hIndex == null || hIndex == 0) {
            if (topField != null && !topField.isBlank()) {
                return "Tác giả " + authorName + " hoạt động trong lĩnh vực " + topField
                        + ". Dữ liệu trích dẫn chưa đủ để đánh giá chỉ số h-index.";
            }
            return "Tác giả " + authorName + " chưa có đủ dữ liệu trích dẫn trong hệ thống để đưa ra đánh giá đầy đủ.";
        }

        StringBuilder insight = new StringBuilder();
        insight.append("Tác giả có chỉ số h-index đạt ").append(hIndex);

        if (topField != null && !topField.isBlank()) {
            insight.append(", khẳng định vị thế chuyên gia trong lĩnh vực ").append(topField).append(". ");
        } else {
            insight.append(", thể hiện năng lực nghiên cứu đáng kể. ");
        }

        if ("Đang sung sức".equals(status)) {
            insight.append("Tác giả vẫn đang tích cực công bố nghiên cứu trong những năm gần đây.");
        } else if ("Đã dừng nghiên cứu".equals(status)) {
            insight.append("Tuy nhiên, tác giả dường như đã giảm hoặc dừng công bố trong 3 năm gần đây.");
        }

        return insight.toString();
    }

    // ============================================
    //  PRIVATE HELPERS — Journal Quality
    // ============================================

    private JournalQualityReportResponse buildEmptyJournalReport(String journalName) {
        return JournalQualityReportResponse.builder()
                .reportTitle("Đánh giá chất lượng tạp chí: " + journalName)
                .journalName(journalName)
                .quartile(null)
                .impactFactor(null)
                .score(null)
                .taste("Chưa có dữ liệu về xu hướng đăng tải của tạp chí này.")
                .insight("Chưa tìm thấy dữ liệu về tạp chí \"" + journalName + "\" trong hệ thống.")
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

    private String generateJournalTaste(List<String> recentKeywords) {
        if (recentKeywords == null || recentKeywords.isEmpty()) {
            return "Chưa có đủ dữ liệu để xác định xu hướng đăng tải gần đây của tạp chí này.";
        }
        if (recentKeywords.size() == 1) {
            return "Tạp chí đang ưu tiên đăng tải các nghiên cứu về "
                    + recentKeywords.get(0) + " trong 2 năm gần đây.";
        }
        String keywordA = recentKeywords.get(0);
        String keywordB = recentKeywords.get(1);
        return "Tạp chí đang ưu tiên đăng tải các nghiên cứu về "
                + keywordA + ", " + keywordB + " trong 2 năm gần đây.";
    }

    private String generateJournalInsight(String quartile, String journalName, java.math.BigDecimal impactFactor) {
        if (quartile == null) {
            return "Tạp chí \"" + journalName + "\" chưa có xếp hạng quartile trong hệ thống.";
        }

        String ifStr = impactFactor != null ? " (IF=" + impactFactor + ")" : "";

        return switch (quartile.toUpperCase()) {
            case "Q1" -> "Đây là tạp chí nhóm Q1" + ifStr
                    + ", phù hợp cho các nghiên cứu chuyên sâu, có tính đột phá cao. "
                    + "Tỷ lệ chấp nhận thường thấp, đòi hỏi chất lượng nghiên cứu xuất sắc.";
            case "Q2" -> "Đây là tạp chí nhóm Q2" + ifStr
                    + ", có uy tín tốt và phù hợp cho các nghiên cứu chất lượng cao. "
                    + "Cân bằng tốt giữa độ uy tín và khả năng được chấp nhận.";
            case "Q3" -> "Đây là tạp chí nhóm Q3" + ifStr
                    + ", phù hợp cho các nghiên cứu ở mức chuyên ngành hẹp hoặc mới bắt đầu. "
                    + "Tỷ lệ chấp nhận cao hơn so với nhóm Q1-Q2.";
            case "Q4" -> "Đây là tạp chí nhóm Q4" + ifStr
                    + ", phù hợp cho các nghiên cứu bước đầu hoặc báo cáo ngắn. "
                    + "Cần cân nhắc kỹ về độ uy tín khi lựa chọn xuất bản.";
            default -> "Tạp chí \"" + journalName + "\" có xếp hạng " + quartile + ifStr + ".";
        };
    }

    // ============================================
    //  MATH HELPERS
    // ============================================

    private short currentYear() {
        return (short) Year.now().getValue();
    }

    private Double roundToOneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
