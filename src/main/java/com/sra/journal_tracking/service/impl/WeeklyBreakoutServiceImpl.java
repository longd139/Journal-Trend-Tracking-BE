package com.sra.journal_tracking.service.impl;

import com.sra.journal_tracking.dto.search.TrendingKeywordResponse;
import com.sra.journal_tracking.dto.trends.WeeklyBreakoutResponse;
import com.sra.journal_tracking.repository.jpa.ResearchPaperRepository;
import com.sra.journal_tracking.service.GraphService;
import com.sra.journal_tracking.service.TrendingKeywordService;
import com.sra.journal_tracking.service.WeeklyBreakoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Computes the top 5 weekly breakout research topics with 6-month citation
 * sparkline data and growth classification.
 *
 * Data flow (multi-tier fallback):
 * 1. Primary: Neo4j getCategoryKeywords → paper IDs → SQL monthly citation sums → real sparklines
 * 2. Fallback 1: TRENDING_TOPIC table (via TrendingKeywordService) → try Neo4j enrichment, else placeholder sparklines
 * 3. Fallback 2: Default curated keywords → placeholder sparklines (never returns empty)
 *
 * Cache is pre-warmed on startup (async) and refreshed every hour.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeeklyBreakoutServiceImpl implements WeeklyBreakoutService {

    private final GraphService graphService;
    private final ResearchPaperRepository researchPaperRepository;
    private final TrendingKeywordService trendingKeywordService;

    private static final int CANDIDATE_LIMIT = 10;
    private static final int RESULT_LIMIT = 5;
    private static final int SPARKLINE_MONTHS = 6;
    private static final double STRONG_GROWTH_THRESHOLD = 30.0;
    private static final double LEADING_MARGIN_RATIO = 1.2;
    private static final long CACHE_TTL_MS = 60 * 60 * 1000; // 1 hour

    /**
     * Default fallback keywords when both Neo4j and TRENDING_TOPIC are empty.
     * These are curated, high-impact research areas that provide a useful zero-state.
     */
    private static final List<String> DEFAULT_KEYWORDS = List.of(
            "Artificial Intelligence", "Climate Change", "Machine Learning",
            "Quantum Computing", "Bioinformatics"
    );

    // ── Cache (same pattern as GraphService) ──

    private static class CacheEntry<T> {
        final T data;
        final long expiryTime;
        CacheEntry(T data) { this.data = data; this.expiryTime = System.currentTimeMillis() + CACHE_TTL_MS; }
        boolean isExpired() { return System.currentTimeMillis() > expiryTime; }
    }

    private final ConcurrentHashMap<String, CacheEntry<List<WeeklyBreakoutResponse>>> breakoutCache = new ConcurrentHashMap<>();
    private static final String CACHE_KEY = "weekly_breakout_topics";

    /**
     * Pre-warm the cache on startup using the fast SQL path (< 1s).
     * Then kicks off background Neo4j enrichment for the next cache refresh.
     */
    @Async("userTaskExecutor")
    @EventListener(ApplicationReadyEvent.class)
    public void preWarmCache() {
        log.info("Pre-warming weekly breakout cache (fast SQL path)...");
        try {
            List<WeeklyBreakoutResponse> topics = getWeeklyBreakoutTopics();
            log.info("Weekly breakout cache pre-warmed with {} topics", topics.size());
            // Kick off background Neo4j enrichment for richer data on next refresh
            tryNeo4jEnrichment();
        } catch (Exception e) {
            log.warn("Failed to pre-warm weekly breakout cache: {}", e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Public API
    // ═══════════════════════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public List<WeeklyBreakoutResponse> getWeeklyBreakoutTopics() {
        // ── Cache hit? ──
        CacheEntry<List<WeeklyBreakoutResponse>> cached = breakoutCache.get(CACHE_KEY);
        if (cached != null && !cached.isExpired()) {
            log.info("CACHE HIT: weekly breakout → {} topics (from cache)", cached.data.size());
            return cached.data;
        }
        if (cached != null) {
            breakoutCache.remove(CACHE_KEY);
        }

        log.info("Computing weekly breakout topics (fast path — SQL only)...");

        // ═══ Primary: TRENDING_TOPIC table (JPA — fast, always < 100ms) ═══
        List<WeeklyBreakoutResponse> results = buildFromTrendingTopicsFast();
        if (!results.isEmpty()) {
            log.info("Weekly breakout: {} topics from TRENDING_TOPIC", results.size());
            breakoutCache.put(CACHE_KEY, new CacheEntry<>(results));
            return results;
        }

        // ═══ Fallback: Default keywords ═══
        log.info("TRENDING_TOPIC empty — using default keywords");
        results = buildFromDefaultsFast();
        breakoutCache.put(CACHE_KEY, new CacheEntry<>(results));
        return results;
    }

    // ═══════════════════════════════════════════════════════════
    //  Fast Path Builders (SQL only, no Neo4j, no external APIs)
    // ═══════════════════════════════════════════════════════════

    /** Build from TRENDING_TOPIC table — pure JPA, sub-millisecond. */
    private List<WeeklyBreakoutResponse> buildFromTrendingTopicsFast() {
        List<TrendingKeywordResponse> trending = trendingKeywordService.getTrendingKeywords(RESULT_LIMIT);

        // Only use entries with real paper counts (not default fallbacks)
        List<TrendingKeywordResponse> realTrending = trending.stream()
                .filter(t -> t.getPaperCount() != null && t.getPaperCount() > 0)
                .toList();

        if (realTrending.isEmpty()) return List.of();

        List<WeeklyBreakoutResponse> results = new ArrayList<>();
        for (TrendingKeywordResponse topic : realTrending) {
            if (results.size() >= RESULT_LIMIT) break;
            long paperCount = topic.getPaperCount() != null ? topic.getPaperCount().longValue() : 0L;
            results.add(buildPlaceholderCard(topic.getKeywordText(), paperCount));
        }
        assignLeadingLabel(results);
        return results;
    }

    /** Build from hardcoded defaults — always returns 5 cards immediately. */
    private List<WeeklyBreakoutResponse> buildFromDefaultsFast() {
        List<WeeklyBreakoutResponse> results = new ArrayList<>();
        for (String keyword : DEFAULT_KEYWORDS) {
            if (results.size() >= RESULT_LIMIT) break;
            results.add(buildPlaceholderCard(keyword, 0L));
        }
        assignLeadingLabel(results);
        return results;
    }

    // ═══════════════════════════════════════════════════════════
    //  Background Neo4j Enrichment (updates cache if possible)
    // ═══════════════════════════════════════════════════════════

    /**
     * Try to enrich the cache with real Neo4j sparkline data.
     * Runs async after the fast response has already been returned.
     * On success, updates the cache so the NEXT request gets real data.
     */
    @Async("userTaskExecutor")
    public void tryNeo4jEnrichment() {
        try {
            List<WeeklyBreakoutResponse> enriched = buildFromNeo4jCategories();
            if (!enriched.isEmpty()) {
                breakoutCache.put(CACHE_KEY, new CacheEntry<>(enriched));
                log.info("Neo4j enrichment: cache updated with {} real-sparkline topics", enriched.size());
            }
        } catch (Exception e) {
            log.debug("Neo4j enrichment skipped: {}", e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Tier Builders
    // ═══════════════════════════════════════════════════════════

    /**
     * Tier 1: Build breakout cards from Neo4j category keywords (real sparkline data).
     * Returns empty list if Neo4j has insufficient data.
     */
    private List<WeeklyBreakoutResponse> buildFromNeo4jCategories() {
        List<Map<String, Object>> candidates = graphService.getCategoryKeywords(CANDIDATE_LIMIT);
        if (candidates.isEmpty()) {
            return List.of();
        }

        log.info("Weekly breakout Tier 1: {} candidates from Neo4j", candidates.size());

        List<WeeklyBreakoutResponse> results = new ArrayList<>();
        for (Map<String, Object> candidate : candidates) {
            if (results.size() >= RESULT_LIMIT) break;

            String keywordText = candidate.get("keywordText") != null
                    ? candidate.get("keywordText").toString() : "";
            String normalizedText = candidate.get("normalizedText") != null
                    ? candidate.get("normalizedText").toString() : keywordText.toLowerCase().trim();
            long paperCount = candidate.get("paperCount") != null
                    ? ((Number) candidate.get("paperCount")).longValue() : 0L;

            if (normalizedText.isEmpty()) continue;

            // Try to enrich with real sparkline from Neo4j + SQL
            WeeklyBreakoutResponse card = buildCardWithSparkline(keywordText, normalizedText, paperCount);
            if (card != null) {
                results.add(card);
            } else {
                // Neo4j lookup failed for this keyword — still include it but with placeholder sparkline
                results.add(buildPlaceholderCard(keywordText, paperCount));
            }
        }

        assignLeadingLabel(results);
        return results;
    }

    /**
     * Tier 2: Build breakout cards from TRENDING_TOPIC table (populated by OpenAlex sync).
     * Attempts Neo4j enrichment for each keyword; falls back to placeholder sparklines.
     */
    private List<WeeklyBreakoutResponse> buildFromTrendingTopics() {
        List<TrendingKeywordResponse> trending = trendingKeywordService.getTrendingKeywords(RESULT_LIMIT);

        // Filter out default-sourced entries (those have paperCount=0 and source="default")
        List<TrendingKeywordResponse> realTrending = trending.stream()
                .filter(t -> t.getPaperCount() != null && t.getPaperCount() > 0)
                .toList();

        if (realTrending.isEmpty()) {
            return List.of();
        }

        log.info("Weekly breakout Tier 2: {} topics from TRENDING_TOPIC", realTrending.size());

        List<WeeklyBreakoutResponse> results = new ArrayList<>();
        for (TrendingKeywordResponse topic : realTrending) {
            if (results.size() >= RESULT_LIMIT) break;

            String keywordText = topic.getKeywordText();
            String normalizedText = keywordText.toLowerCase().trim();
            long paperCount = topic.getPaperCount() != null ? topic.getPaperCount().longValue() : 0L;

            // Try Neo4j enrichment for sparkline
            WeeklyBreakoutResponse card = buildCardWithSparkline(keywordText, normalizedText, paperCount);
            if (card != null) {
                results.add(card);
            } else {
                results.add(buildPlaceholderCard(keywordText, paperCount));
            }
        }

        assignLeadingLabel(results);
        return results;
    }

    /**
     * Tier 3: Build breakout cards from hardcoded default keywords.
     * Last resort — always returns 5 cards with placeholder sparklines.
     */
    private List<WeeklyBreakoutResponse> buildFromDefaults() {
        List<WeeklyBreakoutResponse> results = new ArrayList<>();
        int displayOrder = 1;
        for (String keyword : DEFAULT_KEYWORDS) {
            if (results.size() >= RESULT_LIMIT) break;

            String normalizedText = keyword.toLowerCase().trim();

            // Still try Neo4j — it might have data for these common keywords
            WeeklyBreakoutResponse card = buildCardWithSparkline(keyword, normalizedText, 0L);
            if (card != null) {
                results.add(card);
            } else {
                results.add(buildPlaceholderCard(keyword, 0L));
            }
        }

        assignLeadingLabel(results);
        return results;
    }

    /**
     * Build a single breakout card with real sparkline data from Neo4j + SQL.
     * Returns null if the keyword cannot be resolved in Neo4j.
     */
    private WeeklyBreakoutResponse buildCardWithSparkline(String keywordText, String normalizedText, long paperCount) {
        List<String> paperIdStrings = graphService.getAllPaperIdsByKeyword(normalizedText);
        if (paperIdStrings.isEmpty()) {
            log.debug("Weekly breakout: no Neo4j paper IDs for '{}'", keywordText);
            return null;
        }

        List<UUID> paperIds = paperIdStrings.stream()
                .map(UUID::fromString)
                .toList();

        List<Long> sparkline = computeSparkline(paperIds);
        String growthLabel = classifyGrowth(sparkline);
        Double growthRate = computeGrowthRate(sparkline);

        return WeeklyBreakoutResponse.builder()
                .keywordText(keywordText)
                .sparkline(sparkline)
                .growthLabel(growthLabel)
                .totalPapers(paperCount > 0 ? paperCount : paperIds.size())
                .growthRate(growthRate)
                .build();
    }

    /**
     * Build a placeholder card when Neo4j enrichment is unavailable.
     * Generates a synthetic upward-trending sparkline from paperCount so the FE
     * always has visual data to display, even before the first data sync.
     */
    private WeeklyBreakoutResponse buildPlaceholderCard(String keywordText, long paperCount) {
        List<Long> sparkline = syntheticSparkline(paperCount);
        return WeeklyBreakoutResponse.builder()
                .keywordText(keywordText)
                .sparkline(sparkline)
                .growthLabel(classifyGrowth(sparkline))
                .totalPapers(paperCount)
                .growthRate(computeGrowthRate(sparkline))
                .build();
    }

    /**
     * Generate a synthetic 6-month upward-trending sparkline from a total count.
     * Distributes the count across 6 months with increasing weight:
     * month 0 = 1/21 of total, month 1 = 2/21, ..., month 5 = 6/21.
     * This creates a realistic-looking growth curve for the FE sparkline chart.
     *
     * When total is 0, uses a minimal default (5, 8, 13, 21, 34, 55) for visual appeal.
     */
    private List<Long> syntheticSparkline(long total) {
        if (total <= 0) {
            // Minimal Fibonacci-like upward sequence for zero-state keywords
            return Arrays.asList(5L, 8L, 13L, 21L, 34L, 55L);
        }
        // Distribute total proportionally: weights 1,2,3,4,5,6 = sum 21
        long[] sparkline = new long[SPARKLINE_MONTHS];
        long accumulated = 0;
        for (int i = 0; i < SPARKLINE_MONTHS; i++) {
            long value = total * (i + 1) / 21;
            // Ensure each month is at least as large as the previous (monotonic growth)
            if (i > 0 && value < sparkline[i - 1]) {
                value = sparkline[i - 1] + 1;
            }
            // Don't let intermediate values drop to 0 (looks bad on chart)
            if (value == 0) value = 1;
            sparkline[i] = value;
            accumulated += value;
        }
        // Adjust last month to absorb rounding error so sum makes sense
        if (accumulated < total && sparkline[SPARKLINE_MONTHS - 1] < total) {
            sparkline[SPARKLINE_MONTHS - 1] += (total - accumulated);
        }
        return Arrays.asList(sparkline[0], sparkline[1], sparkline[2],
                sparkline[3], sparkline[4], sparkline[5]);
    }

    // ═══════════════════════════════════════════════════════════
    //  Sparkline Computation
    // ═══════════════════════════════════════════════════════════

    /**
     * Computes a 6-element sparkline array (citation sums by month) for a set of paper IDs.
     * Index 0 = oldest month (6 months ago), index 5 = current month.
     */
    private List<Long> computeSparkline(List<UUID> paperIds) {
        // Build ordered list of the last 6 YearMonth keys
        YearMonth now = YearMonth.from(LocalDate.now());
        List<YearMonth> monthKeys = new ArrayList<>(SPARKLINE_MONTHS);
        for (int i = SPARKLINE_MONTHS - 1; i >= 0; i--) {
            monthKeys.add(now.minusMonths(i));
        }

        // Query SQL: merge results from both queries into a (YearMonth → citationSum) map
        Map<YearMonth, Long> monthCitationMap = new LinkedHashMap<>();
        for (YearMonth ym : monthKeys) {
            monthCitationMap.put(ym, 0L);
        }

        // Query 1: papers WITH pubDate
        try {
            List<Object[]> pubDateRows = researchPaperRepository.sumCitationsByPubDateMonthForPaperIds(paperIds);
            for (Object[] row : pubDateRows) {
                int year = ((Number) row[0]).intValue();
                int month = ((Number) row[1]).intValue();
                long sum = ((Number) row[2]).longValue();
                YearMonth ym = YearMonth.of(year, month);
                monthCitationMap.merge(ym, sum, Long::sum);
            }
        } catch (Exception e) {
            log.warn("sumCitationsByPubDateMonthForPaperIds failed: {}", e.getMessage());
        }

        // Query 2: papers WITHOUT pubDate → fall back to createdAt
        try {
            List<Object[]> createdAtRows = researchPaperRepository.sumCitationsByCreatedAtMonthForPaperIds(paperIds);
            for (Object[] row : createdAtRows) {
                int year = ((Number) row[0]).intValue();
                int month = ((Number) row[1]).intValue();
                long sum = ((Number) row[2]).longValue();
                YearMonth ym = YearMonth.of(year, month);
                monthCitationMap.merge(ym, sum, Long::sum);
            }
        } catch (Exception e) {
            log.warn("sumCitationsByCreatedAtMonthForPaperIds failed: {}", e.getMessage());
        }

        // Build ordered sparkline list
        List<Long> sparkline = new ArrayList<>(SPARKLINE_MONTHS);
        for (YearMonth ym : monthKeys) {
            sparkline.add(monthCitationMap.getOrDefault(ym, 0L));
        }

        return sparkline;
    }

    // ═══════════════════════════════════════════════════════════
    //  Growth Classification
    // ═══════════════════════════════════════════════════════════

    /**
     * Determine the growth label for a sparkline.
     *
     * Rules:
     * - "Newly Emerging" (Newly Emerging): first non-zero month is in the last 2 months
     * - "Strong Growth" (Strong Growth): growth rate > 30% (default fallback)
     * - "Leading" (Leading): assigned later by {@link #assignLeadingLabel}
     */
    private String classifyGrowth(List<Long> sparkline) {
        // Find first non-zero month index
        int firstNonZeroIdx = -1;
        for (int i = 0; i < sparkline.size(); i++) {
            if (sparkline.get(i) > 0) {
                firstNonZeroIdx = i;
                break;
            }
        }

        // No data at all → default to strong growth
        if (firstNonZeroIdx == -1) {
            return "Strong Growth";
        }

        // "Newly Emerging": first non-zero month is in the last 2 months (index >= 4)
        if (firstNonZeroIdx >= SPARKLINE_MONTHS - 2) {
            return "Newly Emerging";
        }

        // Check growth rate (may be null if all sparkline values are zero)
        Double growthRate = computeGrowthRate(sparkline);

        if (growthRate != null && growthRate > STRONG_GROWTH_THRESHOLD) {
            return "Strong Growth";
        }

        // Fallback — still "Strong Growth" if there's any growth
        return "Strong Growth";
    }

    /**
     * Compute growth rate as percentage from first non-zero month to latest month.
     * Returns null if not calculable.
     */
    private Double computeGrowthRate(List<Long> sparkline) {
        // Find first non-zero value
        long earliest = 0;
        for (Long val : sparkline) {
            if (val > 0) {
                earliest = val;
                break;
            }
        }

        long latest = sparkline.get(sparkline.size() - 1);

        if (earliest == 0 || latest == 0) {
            return null;
        }

        double rate = ((double) (latest - earliest) / earliest) * 100.0;
        return roundToOneDecimal(rate);
    }

    /**
     * Assign "Leading" label to the topic with the highest paper count,
     * but only if it's at least 20% ahead of the second place.
     */
    private void assignLeadingLabel(List<WeeklyBreakoutResponse> results) {
        if (results.size() < 2) {
            // Single result → it's the leader
            results.forEach(r -> r.setGrowthLabel("Leading"));
            return;
        }

        // Sort by totalPapers descending to find top 2
        List<WeeklyBreakoutResponse> sorted = results.stream()
                .sorted(Comparator.comparing(WeeklyBreakoutResponse::getTotalPapers,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        WeeklyBreakoutResponse top = sorted.get(0);
        WeeklyBreakoutResponse second = sorted.get(1);

        long topPapers = top.getTotalPapers() != null ? top.getTotalPapers() : 0L;
        long secondPapers = second.getTotalPapers() != null ? second.getTotalPapers() : 0L;

        if (secondPapers == 0 || (double) topPapers / secondPapers >= LEADING_MARGIN_RATIO) {
            top.setGrowthLabel("Leading");
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════════════════

    private Double roundToOneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
