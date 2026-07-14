package com.sra.journal_tracking.service.impl;

import com.sra.journal_tracking.constants.KeywordConstants;
import com.sra.journal_tracking.dto.paper.AuthorDTO;
import com.sra.journal_tracking.dto.paper.KeywordDTO;
import com.sra.journal_tracking.dto.paper.KeywordQuickStatsResponse;
import com.sra.journal_tracking.dto.paper.PaperDetailResponseDTO;
import com.sra.journal_tracking.dto.paper.RelatedKeywordResponse;
import com.sra.journal_tracking.dto.paper.TopJournalResponse;
import com.sra.journal_tracking.dto.search.KeywordComparisonRequest;
import com.sra.journal_tracking.dto.search.KeywordComparisonResponse;
import com.sra.journal_tracking.dto.sync.OpenAlexResponseDTO;
import com.sra.journal_tracking.entity.jpa.ResearchPaper;
import com.sra.journal_tracking.repository.jpa.ResearchPaperRepository;
import com.sra.journal_tracking.service.DataSyncService;
import com.sra.journal_tracking.service.GraphService;
import com.sra.journal_tracking.service.KeywordQuickStatsService;
import com.sra.journal_tracking.service.OpenAlexFallbackSearchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.time.Year;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Computes quick statistics for a searched keyword by combining
 * Neo4j graph lookups with SQL Server aggregation.
 *
 * Flow:
 * 1. Neo4j → get all paper IDs + count matching the keyword
 * 2. If no papers found → auto-sync from OpenAlex + Semantic Scholar
 * 3. Re-query Neo4j → should now have data
 * 4. SQL → load papers, compute total citations + avg per paper
 * 5. SQL → count papers by pubYear for YoY growth calculation
 */
@Slf4j
@Service
public class KeywordQuickStatsServiceImpl implements KeywordQuickStatsService {

    private final GraphService graphService;
    private final ResearchPaperRepository researchPaperRepository;
    private final DataSyncService dataSyncService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final OpenAlexFallbackSearchService openAlexSearchService;

    @Value("${app.openalex-api-key:}")
    private String openalexApiKey;

    @Value("${app.openalex-email:}")
    private String openalexEmail;

    private static final int SAMPLE_SIZE = 25;
    private static final int MAX_RETRIES = 3;
    private static final int TOP_JOURNALS_LIMIT = 10;

    public KeywordQuickStatsServiceImpl(GraphService graphService,
                                         ResearchPaperRepository researchPaperRepository,
                                         DataSyncService dataSyncService,
                                         RestTemplate restTemplate,
                                         ObjectMapper objectMapper,
                                         OpenAlexFallbackSearchService openAlexSearchService) {
        this.graphService = graphService;
        this.researchPaperRepository = researchPaperRepository;
        this.dataSyncService = dataSyncService;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.openAlexSearchService = openAlexSearchService;
    }

    @Override
    @Cacheable(value = "search:keywordQuickStats", cacheManager = "searchCacheManager",
               key = "#keyword.trim().toLowerCase() + ':' + (#pubYearFrom != null ? #pubYearFrom : '') + ':' + (#pubYearTo != null ? #pubYearTo : '') + ':' + (#isOpenAccess != null ? #isOpenAccess : '')",
               unless = "#result == null || #result.totalPapers == 0")
    public KeywordQuickStatsResponse getStats(String keyword, Integer pubYearFrom, Integer pubYearTo, Boolean isOpenAccess) {
        String trimmedKeyword = keyword.trim();
        if (trimmedKeyword.isEmpty()) {
            return buildEmptyResponse(keyword);
        }
        if (trimmedKeyword.length() > KeywordConstants.MAX_KEYWORD_LENGTH) {
            trimmedKeyword = trimmedKeyword.substring(0, KeywordConstants.MAX_KEYWORD_LENGTH);
        }

        log.info("Computing quick stats via OpenAlex API for keyword: '{}' (from={}, to={}, oa={})",
                trimmedKeyword, pubYearFrom, pubYearTo, isOpenAccess);

        // OpenAlex API path (primary)
        String openAlexFilter = buildOpenAlexFilter(pubYearFrom, pubYearTo, isOpenAccess);
        KeywordQuickStatsResponse response = getStatsFromOpenAlex(trimmedKeyword, openAlexFilter);
        if (response != null && response.getTotalPapers() > 0) {
            return response;
        }

        // Fallback to local DB if OpenAlex fails
        log.info("OpenAlex returned no data for '{}', falling back to local DB", trimmedKeyword);
        return getStatsFromLocalDb(trimmedKeyword);
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

    // ═══════════════════════════════════════════════════════════════
    //  OpenAlex API implementation
    // ═══════════════════════════════════════════════════════════════

    private KeywordQuickStatsResponse getStatsFromOpenAlex(String keyword, String openAlexFilter) {
        try {
            // Call 1: Sample papers + total count
            String baseUrl = buildWorksUrl(keyword, SAMPLE_SIZE, "cited_by_count:desc", openAlexFilter);
            OpenAlexResponseDTO r1 = fetchOpenAlexWithRetry(baseUrl, keyword);
            if (r1 == null || r1.getMeta() == null || r1.getMeta().getCount() == 0) return null;

            long totalPapers = r1.getMeta().getCount();
            List<OpenAlexResponseDTO.OpenAlexWorkDTO> works =
                    r1.getResults() != null ? r1.getResults() : List.of();

            long totalCitations = works.stream()
                    .mapToLong(w -> w.getCitedByCount() != null ? w.getCitedByCount() : 0).sum();
            double avgCitations = works.isEmpty() ? 0 : (double) totalCitations / Math.min(SAMPLE_SIZE, totalPapers);

            // Call 2+3: YoY counts
            short thisYear = (short) Year.now().getValue();
            short lastYear = (short) (thisYear - 1);

            String thisYrUrl = buildWorksUrl(keyword, 1, null, "from_publication_date:" + thisYear + "-01-01");
            OpenAlexResponseDTO r2 = fetchOpenAlexWithRetry(thisYrUrl, keyword);
            long papersThisYear = r2 != null && r2.getMeta() != null ? r2.getMeta().getCount() : 0L;

            String lastYrUrl = buildWorksUrl(keyword, 1, null,
                    "from_publication_date:" + lastYear + "-01-01,to_publication_date:" + lastYear + "-12-31");
            OpenAlexResponseDTO r3 = fetchOpenAlexWithRetry(lastYrUrl, keyword);
            long papersLastYear = r3 != null && r3.getMeta() != null ? r3.getMeta().getCount() : 0L;

            Double yoyRate = null;
            String yoyDir = "neutral";
            if (papersLastYear > 0) {
                yoyRate = ((double)(papersThisYear - papersLastYear) / papersLastYear) * 100.0;
                yoyDir = yoyRate > 0 ? "up" : yoyRate < 0 ? "down" : "neutral";
            } else if (papersThisYear > 0) { yoyRate = 100.0; yoyDir = "up"; }

            List<TopJournalResponse> journals = aggregateTopJournals(works);

            log.info("OpenAlex stats for '{}': papers={}, citations={}, yoy={}%",
                    keyword, totalPapers, totalCitations, yoyRate);

            return KeywordQuickStatsResponse.builder()
                    .keyword(keyword).totalPapers(totalPapers).totalCitations(totalCitations)
                    .yoyGrowthRate(yoyRate).yoyGrowthDirection(yoyDir)
                    .avgCitationsPerPaper(roundToOneDecimal(avgCitations))
                    .papersThisYear(papersThisYear).papersLastYear(papersLastYear)
                    .topJournals(journals).build();
        } catch (Exception e) {
            log.warn("OpenAlex stats failed for '{}': {}", keyword, e.getMessage());
            return null;
        }
    }

    private String buildOpenAlexFilter(Integer pubYearFrom, Integer pubYearTo, Boolean isOpenAccess) {
        List<String> filters = new ArrayList<>();
        if (pubYearFrom != null) {
            filters.add("from_publication_date:" + pubYearFrom + "-01-01");
        }
        if (pubYearTo != null) {
            filters.add("to_publication_date:" + pubYearTo + "-12-31");
        }
        if (Boolean.TRUE.equals(isOpenAccess)) {
            filters.add("open_access.is_oa:true");
        }
        return filters.isEmpty() ? null : String.join(",", filters);
    }

    private String buildWorksUrl(String keyword, int perPage, String sort, String filter) {
        var b = UriComponentsBuilder.fromHttpUrl("https://api.openalex.org/works")
                .queryParam("search", keyword).queryParam("per-page", perPage)
                .queryParam("select", "id,doi,title,display_name,cited_by_count,publication_year,"
                        + "primary_location,open_access,topics,keywords,authorships");
        if (sort != null) b.queryParam("sort", sort);
        if (filter != null) b.queryParam("filter", filter);
        if (openalexApiKey != null && !openalexApiKey.isBlank()) b.queryParam("api_key", openalexApiKey);
        else if (openalexEmail != null && !openalexEmail.isBlank()) b.queryParam("mailto", openalexEmail);
        return b.build().toUriString();
    }

    private OpenAlexResponseDTO fetchOpenAlexWithRetry(String url, String keyword) {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                String raw = restTemplate.getForObject(url, String.class);
                if (raw == null || raw.isBlank()) return null;
                return objectMapper.readValue(raw, OpenAlexResponseDTO.class);
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                log.warn("OpenAlex works {} retry {}/{} failed for '{}': {}",
                        attempt > 0 ? "attempt" : "attempt", attempt + 1, MAX_RETRIES, keyword, msg);
                if (attempt < MAX_RETRIES - 1) {
                    try { Thread.sleep((attempt + 1) * 2000L); }
                    catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                }
            }
        }
        log.error("OpenAlex works exhausted {} retries for '{}'", MAX_RETRIES, keyword);
        return null;
    }

    private List<TopJournalResponse> aggregateTopJournals(
            List<OpenAlexResponseDTO.OpenAlexWorkDTO> works) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (var w : works) {
            var src = w.getPrimaryLocation() != null ? w.getPrimaryLocation().getSource() : null;
            if (src != null && src.getDisplayName() != null)
                counts.merge(src.getDisplayName(), 1L, Long::sum);
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(TOP_JOURNALS_LIMIT)
                .map(e -> TopJournalResponse.builder().journalName(e.getKey())
                        .paperCount(e.getValue()).impactFactor(null).quartile(null).publisher(null).build())
                .collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════════
    //  Local DB fallback (Neo4j + SQL)
    // ═══════════════════════════════════════════════════════════════

    private KeywordQuickStatsResponse getStatsFromLocalDb(String trimmedKeyword) {
        long totalPapers = graphService.countPapersByKeyword(trimmedKeyword);
        if (totalPapers == 0) {
            log.info("No papers found for '{}' in local DB", trimmedKeyword);
            return buildEmptyResponse(trimmedKeyword);
        }

        List<String> paperIdStrings = graphService.getAllPaperIdsByKeyword(trimmedKeyword);
        List<UUID> paperIds = paperIdStrings.stream().map(UUID::fromString).toList();
        if (paperIds.isEmpty()) return buildEmptyResponse(trimmedKeyword);

        long totalCitations = researchPaperRepository.sumCitationCountByIds(paperIds);
        if (totalCitations == 0 && !paperIds.isEmpty()) {
            long sqlCit = researchPaperRepository.sumCitationCountByKeyword(trimmedKeyword);
            if (sqlCit > 0) { log.info("SQL fallback citations: {}", sqlCit); totalCitations = sqlCit; }
        }

        double avgCit = (double) totalCitations / totalPapers;
        short thisYear = (short) Year.now().getValue();
        short lastYear = (short) (thisYear - 1);
        long pThisYear = researchPaperRepository.countByPaperIdsAndPubYear(paperIds, thisYear);
        long pLastYear = researchPaperRepository.countByPaperIdsAndPubYear(paperIds, lastYear);

        Double yoyRate = null;
        String yoyDir = "neutral";
        if (pLastYear > 0) { yoyRate = ((double)(pThisYear - pLastYear) / pLastYear) * 100.0; yoyDir = yoyRate > 0 ? "up" : yoyRate < 0 ? "down" : "neutral"; }
        else if (pThisYear > 0) { yoyRate = 100.0; yoyDir = "up"; }

        return KeywordQuickStatsResponse.builder()
                .keyword(trimmedKeyword).totalPapers(totalPapers).totalCitations(totalCitations)
                .yoyGrowthRate(yoyRate).yoyGrowthDirection(yoyDir)
                .avgCitationsPerPaper(roundToOneDecimal(avgCit))
                .papersThisYear(pThisYear).papersLastYear(pLastYear)
                .topJournals(getTopJournals(paperIds)).build();
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
    @Cacheable(value = "search:keywordRelatedTrends", cacheManager = "searchCacheManager",
               key = "#keyword.trim().toLowerCase() + ':' + (#pubYearFrom != null ? #pubYearFrom : '') + ':' + (#pubYearTo != null ? #pubYearTo : '')",
               unless = "#result == null || #result.isEmpty()")
    public List<RelatedKeywordResponse> getRelatedTrends(String keyword, Integer pubYearFrom, Integer pubYearTo) {
        String trimmedKeyword = keyword.trim();
        if (trimmedKeyword.isEmpty()) {
            return List.of();
        }

        if (trimmedKeyword.length() > KeywordConstants.MAX_KEYWORD_LENGTH) {
            trimmedKeyword = trimmedKeyword.substring(0, KeywordConstants.MAX_KEYWORD_LENGTH);
        }

        short thisYear = (short) Year.now().getValue();
        short lastYear = (short) (thisYear - 1);
        // Default: last 2 years; override with user-provided filter
        int startYear = pubYearFrom != null ? pubYearFrom : thisYear - 2;

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
    @Cacheable(value = "search:keywordTopPapers", cacheManager = "searchCacheManager",
               key = "#keyword.trim().toLowerCase() + ':' + (#yearFrom != null ? #yearFrom : '') + ':' + (#yearTo != null ? #yearTo : '')",
               unless = "#result == null || #result.isEmpty()")
    public List<PaperDetailResponseDTO> getTopInfluentialPapers(String keyword, Integer yearFrom, Integer yearTo) {
        String trimmedKeyword = keyword.trim();
        if (trimmedKeyword.isEmpty()) return List.of();
        if (trimmedKeyword.length() > KeywordConstants.MAX_KEYWORD_LENGTH)
            trimmedKeyword = trimmedKeyword.substring(0, KeywordConstants.MAX_KEYWORD_LENGTH);

        log.info("Fetching top influential papers via OpenAlex for: '{}' (from={}, to={})",
                trimmedKeyword, yearFrom, yearTo);

        // Call OpenAlex /works — sorted by cited_by_count:desc, with optional year filter
        List<PaperDetailResponseDTO> papers = openAlexSearchService.searchTopCited(trimmedKeyword, 5, yearFrom, yearTo);
        log.info("OpenAlex top papers for '{}': {} results", trimmedKeyword, papers.size());
        return papers;
    }

    private static final long SYNC_TIMEOUT_SECONDS = 10;

    /**
     * Execute an external sync call with a hard timeout.
     * Returns true if the sync completed within the deadline, false if it timed out or failed.
     * The sync runs in a background thread so it can continue writing to DB even after we move on.
     */
    private boolean syncWithTimeout(Runnable syncTask, String apiName, String keyword) {
        try {
            CompletableFuture.runAsync(syncTask)
                    .get(SYNC_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
            log.info("{} sync completed for '{}'", apiName, keyword);
            return true;
        } catch (java.util.concurrent.TimeoutException e) {
            log.warn("{} sync timed out (>3s) for '{}', skipping", apiName, keyword);
            return false;
        } catch (Exception e) {
            log.warn("{} sync failed for '{}': {}", apiName, keyword, e.getMessage());
            return false;
        }
    }

    /**
     * Fetch top cited papers from local DB (Neo4j → SQL pipeline).
     * Tries Neo4j keyword index first, then falls back to SQL full-text search.
     */
    private List<PaperDetailResponseDTO> fetchTopPapersFromDb(String keyword) {
        // Neo4j index lookup (fast, exact match on normalizedText) → SQL fetch by IDs
        String normalized = keyword.toLowerCase();
        List<String> paperIdStrings = graphService.getAllPaperIdsByKeyword(normalized);

        if (!paperIdStrings.isEmpty()) {
            // Only take first 50 IDs — enough for top-5, faster SQL IN clause
            List<UUID> paperIds = paperIdStrings.stream()
                    .limit(50)
                    .map(UUID::fromString)
                    .toList();

            List<ResearchPaper> topPapers = researchPaperRepository.findTopCitedByIds(
                    paperIds, PageRequest.of(0, 5));

            if (!topPapers.isEmpty()) {
                return topPapers.stream()
                        .map(this::mapToSummaryDTO)
                        .collect(Collectors.toList());
            }
            log.info("Neo4j IDs returned empty from SQL for '{}', falling back to SQL keyword search", keyword);
        } else {
            log.info("No papers found for '{}' in Neo4j, falling back to SQL exact keyword match", keyword);
        }

        // ── SQL fallback (Neo4j out of sync or empty) ──
        List<ResearchPaper> sqlResults = researchPaperRepository.findTopCitedByKeywordExact(
                keyword, PageRequest.of(0, 5));
        if (!sqlResults.isEmpty()) {
            log.info("SQL exact keyword match found {} papers for '{}'", sqlResults.size(), keyword);
            return sqlResults.stream()
                    .map(this::mapToSummaryDTO)
                    .collect(Collectors.toList());
        }

        // Broader LIKE fallback (title, abstract, keyword_text)
        log.info("No exact keyword match for '{}', falling back to SQL full-text LIKE", keyword);
        sqlResults = researchPaperRepository.findTopCitedByKeyword(
                keyword, PageRequest.of(0, 5));
        return sqlResults.stream()
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

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "search:keywordComparison", cacheManager = "recommendationCacheManager",
               key = "T(java.util.Objects).hash(#request.keywords.stream().sorted().toList())",
               unless = "#result == null || #result.keywords.isEmpty()")
    public KeywordComparisonResponse compareKeywords(KeywordComparisonRequest request) {
        List<String> keywords = request.getKeywords().stream()
                .filter(k -> k != null && !k.trim().isEmpty())
                .map(String::trim)
                .distinct()
                .toList();

        if (keywords.isEmpty()) {
            return KeywordComparisonResponse.builder().keywords(List.of()).build();
        }

        List<KeywordComparisonResponse.KeywordComparisonItem> items = keywords.stream()
                .map(this::buildComparisonItem)
                .toList();

        return KeywordComparisonResponse.builder().keywords(items).build();
    }

    /**
     * Build a single keyword comparison data point.
     * Reuses the same Neo4j → SQL pipeline as getStats() but returns
     * a lightweight item suitable for side-by-side BarChart comparison.
     */
    private KeywordComparisonResponse.KeywordComparisonItem buildComparisonItem(String keyword) {
        // Step 1: Paper count from Neo4j
        long paperCount = graphService.countPapersByKeyword(keyword);

        if (paperCount == 0) {
            return KeywordComparisonResponse.KeywordComparisonItem.builder()
                    .name(keyword)
                    .paperCount(0L)
                    .citationCount(0L)
                    .growthRate(null)
                    .topYear(null)
                    .build();
        }

        // Step 2: Get paper IDs for SQL aggregation
        List<String> paperIdStrings = graphService.getAllPaperIdsByKeyword(keyword);
        List<UUID> paperIds = paperIdStrings.stream()
                .map(UUID::fromString)
                .toList();

        // Step 3: Sum citations from SQL
        long citationCount = researchPaperRepository.sumCitationCountByIds(paperIds);

        // Step 4: YoY growth rate (this year vs last year)
        short thisYear = (short) Year.now().getValue();
        short lastYear = (short) (thisYear - 1);

        long papersThisYear = researchPaperRepository.countByPaperIdsAndPubYear(paperIds, thisYear);
        long papersLastYear = researchPaperRepository.countByPaperIdsAndPubYear(paperIds, lastYear);

        Double growthRate = null;
        if (papersLastYear > 0) {
            growthRate = roundToOneDecimal(((double) (papersThisYear - papersLastYear) / papersLastYear) * 100.0);
        } else if (papersThisYear > 0) {
            growthRate = 100.0;
        }

        // Step 5: Find the peak year (year with most publications)
        List<Short> peakYears = researchPaperRepository.findPeakYearByPaperIds(
                paperIds, PageRequest.of(0, 1));
        Integer topYear = peakYears.isEmpty() ? null : peakYears.get(0).intValue();

        return KeywordComparisonResponse.KeywordComparisonItem.builder()
                .name(keyword)
                .paperCount(paperCount)
                .citationCount(citationCount)
                .growthRate(growthRate)
                .topYear(topYear)
                .build();
    }

    private Double roundToOneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
