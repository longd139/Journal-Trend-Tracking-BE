package com.sra.journal_tracking.service;

import com.sra.journal_tracking.entity.jpa.TrendingTopic;
import com.sra.journal_tracking.repository.jpa.TrendingTopicRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Refreshes trending topics from OpenAlex and stores them in DB.
 * - Runs on every application startup (async, non-blocking)
 * - Then refreshes every 12 hours while the app is running
 *
 * The {@code /api/public/keywords/trending} endpoint reads directly from DB
 * so the API always returns instantly with pre-cached data.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrendingTopicSyncService {

    private static final int MAX_TRENDING_TOPICS = 10;
    private static final int FETCH_PER_PAGE = 50;

    private final TrendingTopicRepository trendingTopicRepository;
    private final KeywordExpansionService keywordExpansionService;

    @Value("${app.openalex-email:}")
    private String openalexEmail;

    /** Prevents concurrent sync executions (startup + scheduled overlap). */
    private final AtomicBoolean syncing = new AtomicBoolean(false);

    /**
     * Run immediately after application startup (async, does not block boot).
     * Ensures trending data is fresh even if the app was just turned on.
     */
    @Async("taskExecutor")
    @EventListener(ApplicationReadyEvent.class)
    public void syncOnStartup() {
        log.info("=== TRENDING TOPICS: Startup sync triggered ===");
        doRefresh("startup");
    }

    /**
     * Scheduled refresh every 12 hours (midnight and noon).
     */
    @Scheduled(cron = "0 0 0,12 * * ?")
    @Transactional
    public void refreshTrendingTopics() {
        doRefresh("scheduled");
    }

    private void doRefresh(String trigger) {
        if (!syncing.compareAndSet(false, true)) {
            log.info("Trending sync: Already syncing — skipping {} trigger", trigger);
            return;
        }

        log.info("=== TRENDING TOPICS SYNC: {} refresh ===", trigger);
        LocalDateTime start = LocalDateTime.now();

        try {
            List<TrendingTopic> topics = fetchTrendingFromOpenAlex();

            if (topics.isEmpty()) {
                log.warn("Trending sync ({}): No topics from OpenAlex. Keeping existing data.", trigger);
                return;
            }

            // Replace all existing topics with fresh data
            trendingTopicRepository.deleteAll();
            trendingTopicRepository.flush();

            // Assign display order 1..N
            for (int i = 0; i < topics.size(); i++) {
                topics.get(i).setDisplayOrder(i + 1);
            }

            trendingTopicRepository.saveAll(topics);
            log.info("=== TRENDING TOPICS SYNC DONE ({}): {} topics in {} ===",
                    trigger, topics.size(), java.time.Duration.between(start, LocalDateTime.now()).toSeconds() + "s");

        } catch (Exception e) {
            log.error("Trending topics sync failed ({}): {}", trigger, e.getMessage(), e);
        } finally {
            syncing.set(false);
        }
    }

    // ═══════════════════════════════════════════════
    //  Fetch from OpenAlex
    // ═══════════════════════════════════════════════

    private List<TrendingTopic> fetchTrendingFromOpenAlex() {
        Map<String, Integer> keywordCounts = new LinkedHashMap<>();

        try {
            String today = LocalDate.now().toString();
            String monthAgo = LocalDate.now().minusDays(30).toString();
            String url = "https://api.openalex.org/works"
                    + "?filter=from_publication_date:" + monthAgo
                    + ",to_publication_date:" + today
                    + ",is_paratext:false"
                    + "&sort=cited_by_count:desc"
                    + "&per_page=" + FETCH_PER_PAGE
                    + "&select=title,concepts,keywords";
            if (openalexEmail != null && !openalexEmail.isBlank()) {
                url += "&mailto=" + openalexEmail;
            }

            log.info("Trending sync: Fetching from OpenAlex — {}", url);

            RestTemplate restTemplate = new RestTemplate();
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response == null) return Collections.emptyList();

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");
            if (results == null || results.isEmpty()) return Collections.emptyList();

            // Aggregate keywords from paper titles and concepts
            for (Map<String, Object> work : results) {
                String title = (String) work.get("title");
                if (title != null) {
                    for (String token : keywordExpansionService.extractTokens(title)) {
                        if (token.length() > 3) {
                            keywordCounts.merge(token.toLowerCase().trim(), 1, Integer::sum);
                        }
                    }
                }

                // Extract concept display names
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> concepts = (List<Map<String, Object>>) work.get("concepts");
                if (concepts != null) {
                    for (Map<String, Object> concept : concepts) {
                        String displayName = (String) concept.get("display_name");
                        if (displayName != null && displayName.length() > 3) {
                            keywordCounts.merge(displayName.toLowerCase().trim(), 1, Integer::sum);
                        }
                    }
                }

                // Extract keyword display names
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> keywords = (List<Map<String, Object>>) work.get("keywords");
                if (keywords != null) {
                    for (Map<String, Object> kw : keywords) {
                        String displayName = (String) kw.get("display_name");
                        if (displayName != null && displayName.length() > 3) {
                            keywordCounts.merge(displayName.toLowerCase().trim(), 1, Integer::sum);
                        }
                    }
                }
            }

        } catch (Exception e) {
            log.error("Failed to fetch trending from OpenAlex: {}", e.getMessage(), e);
            return Collections.emptyList();
        }

        // Filter stop words, sort by count, take top N
        return keywordCounts.entrySet().stream()
                .filter(e -> !isStopWord(e.getKey()))
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(MAX_TRENDING_TOPICS)
                .map(e -> TrendingTopic.builder()
                        .topicName(capitalizeWords(e.getKey()))
                        .paperCount(e.getValue())
                        .source("openalex")
                        .build())
                .collect(Collectors.toList());
    }

    private boolean isStopWord(String word) {
        Set<String> stopWords = Set.of(
                "the", "and", "for", "from", "with", "that", "this", "have", "been",
                "their", "which", "based", "using", "into", "also", "more", "some",
                "over", "than", "other", "such", "these", "about", "after", "between",
                "study", "research", "data", "analysis", "review", "approach",
                "method", "results", "paper", "model", "effect", "role", "used",
                "new", "two", "one", "can", "may", "well", "found", "show",
                "journal", "science", "university", "international", "evidence",
                "introduction", "background", "conclusion", "discussion", "methods"
        );
        return stopWords.contains(word.toLowerCase());
    }

    private String capitalizeWords(String text) {
        if (text == null || text.isBlank()) return text;
        return Arrays.stream(text.split("\\s+"))
                .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1))
                .collect(Collectors.joining(" "));
    }
}
