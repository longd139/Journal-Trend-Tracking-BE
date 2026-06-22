package com.sra.journal_tracking.service;

import com.sra.journal_tracking.entity.jpa.SyncLog;
import com.sra.journal_tracking.repository.jpa.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Auto-sync trending topics from external sources.
 * Runs daily at 2 AM. Controlled by SystemConfig key "auto_sync_enabled".
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledDataSyncService {

    private static final int MAX_TRENDING_PAPERS = 15;
    private static final int MAX_KEYWORDS = 8;
    private static final int SYNC_PER_KEYWORD = 10;

    private final DataSyncService dataSyncService;
    private final SystemConfigRepository systemConfigRepository;
    private final KeywordExpansionService keywordExpansionService;

    @Value("${app.core-api-key:}")
    private String coreApiKey;

    @Value("${app.openalex-email:}")
    private String openalexEmail;

    @Scheduled(cron = "0 0 * * * ?") // Every hour
    public void autoSyncTrendingTopics() {
        if (!isAutoSyncEnabled()) {
            log.info("Auto-sync is DISABLED. Skipping.");
            return;
        }

        log.info("=== AUTO-SYNC: Starting trending topic sync ===");
        LocalDateTime startTime = LocalDateTime.now();
        int totalPapersInserted = 0;

        try {
            // Step 1: Fetch trending keywords from external sources
            Set<String> trendingKeywords = new LinkedHashSet<>();
            trendingKeywords.addAll(fetchTrendingFromOpenAlex());
            trendingKeywords.addAll(fetchTrendingFromSemanticScholar());

            if (trendingKeywords.isEmpty()) {
                log.info("Auto-sync: No trending keywords found. Falling back to defaults.");
                trendingKeywords.addAll(getDefaultTopics());
            }

            log.info("Auto-sync: {} trending keywords identified: {}", trendingKeywords.size(),
                    trendingKeywords.stream().limit(5).collect(Collectors.joining(", ")));

            // Step 2: Sync papers for each keyword
            for (String keyword : trendingKeywords) {
                try {
                    SyncLog log1 = dataSyncService.syncFromOpenAlex(keyword, SYNC_PER_KEYWORD);
                    totalPapersInserted += log1.getPapersInserted() != null ? log1.getPapersInserted() : 0;
                    Thread.sleep(2000); // Rate limit

                    SyncLog log2 = dataSyncService.syncFromSemanticScholar(keyword, SYNC_PER_KEYWORD);
                    totalPapersInserted += log2.getPapersInserted() != null ? log2.getPapersInserted() : 0;
                    Thread.sleep(2000);

                    // arXiv rate limit: 1 req/3s
                    try {
                        SyncLog log3 = dataSyncService.syncFromArxiv(keyword, Math.min(SYNC_PER_KEYWORD, 5));
                        totalPapersInserted += log3.getPapersInserted() != null ? log3.getPapersInserted() : 0;
                        Thread.sleep(4000);
                    } catch (Exception e) {
                        log.debug("arXiv sync skipped for '{}': {}", keyword, e.getMessage());
                    }

                    // CORE (if key available)
                    if (coreApiKey != null && !coreApiKey.isBlank()) {
                        try {
                            SyncLog log4 = dataSyncService.syncFromCore(keyword, SYNC_PER_KEYWORD);
                            totalPapersInserted += log4.getPapersInserted() != null ? log4.getPapersInserted() : 0;
                            Thread.sleep(1000);
                        } catch (Exception e) {
                            log.debug("CORE sync skipped for '{}': {}", keyword, e.getMessage());
                        }
                    }

                    // Save current keyword set to DB
                    saveAutoSyncResult(totalPapersInserted, startTime);

                } catch (Exception e) {
                    log.warn("Auto-sync failed for keyword '{}': {}", keyword, e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("Auto-sync failed: {}", e.getMessage(), e);
            saveAutoSyncResult(totalPapersInserted, startTime);
        }

        log.info("=== AUTO-SYNC DONE: {} new papers inserted ===", totalPapersInserted);
    }

    // ═══════════════════════════════════════════════
    //  Fetch trending keywords from external sources
    // ═══════════════════════════════════════════════

    private List<String> fetchTrendingFromOpenAlex() {
        List<String> keywords = new ArrayList<>();
        try {
            String today = LocalDate.now().toString();
            String monthAgo = LocalDate.now().minusDays(30).toString();
            String url = "https://api.openalex.org/works"
                    + "?filter=from_publication_date:" + monthAgo + ",to_publication_date:" + today
                    + ",is_paratext:false"
                    + "&sort=cited_by_count:desc"
                    + "&per_page=" + MAX_TRENDING_PAPERS
                    + "&select=title,keywords";
            if (openalexEmail != null && !openalexEmail.isBlank()) {
                url += "&mailto=" + openalexEmail;
            }
            log.debug("Fetching trending from OpenAlex: {}", url);

            var response = new org.springframework.web.client.RestTemplate()
                    .getForObject(url, Map.class);
            if (response == null) return keywords;

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");
            if (results == null) return keywords;

            for (Map<String, Object> work : results) {
                String title = (String) work.get("title");
                if (title != null) {
                    keywords.addAll(keywordExpansionService.extractTokens(title));
                }
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> kws = (List<Map<String, Object>>) work.get("keywords");
                if (kws != null) {
                    for (Map<String, Object> kw : kws) {
                        String text = (String) kw.get("display_name");
                        if (text != null && text.length() > 3) keywords.add(text.toLowerCase().trim());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch trending from OpenAlex: {}", e.getMessage());
        }
        return keywords.stream().distinct().limit(MAX_KEYWORDS).collect(Collectors.toList());
    }

    private List<String> fetchTrendingFromSemanticScholar() {
        List<String> keywords = new ArrayList<>();
        try {
            String url = "https://api.semanticscholar.org/graph/v1/paper/search"
                    + "?query=artificial+intelligence+machine+learning"
                    + "&year=" + LocalDate.now().getYear()
                    + "&sort=publicationDate:desc"
                    + "&fields=title"
                    + "&limit=" + MAX_TRENDING_PAPERS;
            log.debug("Fetching trending from Semantic Scholar: {}", url);

            var response = new org.springframework.web.client.RestTemplate()
                    .getForObject(url, Map.class);
            if (response == null) return keywords;

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
            if (data == null) return keywords;

            for (Map<String, Object> paper : data) {
                String title = (String) paper.get("title");
                if (title != null) {
                    keywords.addAll(keywordExpansionService.extractTokens(title));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch trending from Semantic Scholar: {}", e.getMessage());
        }
        return keywords.stream().filter(k -> k.length() > 3).distinct().limit(MAX_KEYWORDS)
                .collect(Collectors.toList());
    }

    private List<String> getDefaultTopics() {
        return List.of("artificial intelligence", "machine learning", "deep learning",
                "data science", "computer vision", "natural language processing");
    }

    // ═══════════════════════════════════════════════
    //  SystemConfig helpers
    // ═══════════════════════════════════════════════

    private boolean isAutoSyncEnabled() {
        return systemConfigRepository.findByConfigKey("auto_sync_enabled")
                .map(c -> "true".equalsIgnoreCase(c.getConfigValue()))
                .orElse(true); // Default: enabled
    }

    @Transactional
    public boolean toggleAutoSync(boolean enabled) {
        var config = systemConfigRepository.findByConfigKey("auto_sync_enabled")
                .orElseGet(() -> {
                    log.info("Creating new SystemConfig: auto_sync_enabled = {}", enabled);
                    return systemConfigRepository.save(
                            com.sra.journal_tracking.entity.jpa.SystemConfig.builder()
                                    .configKey("auto_sync_enabled")
                                    .configValue(String.valueOf(enabled))
                                    .description("Auto-sync enabled/disabled toggle")
                                    .build());
                });
        config.setConfigValue(String.valueOf(enabled));
        config.setUpdatedAt(LocalDateTime.now());
        systemConfigRepository.save(config);
        log.info("Auto-sync toggled to: {}", enabled);
        return enabled;
    }

    public boolean isEnabled() {
        return isAutoSyncEnabled();
    }

    private void saveAutoSyncResult(int totalPapers, LocalDateTime time) {
        try {
            var papersConfig = systemConfigRepository.findByConfigKey("last_auto_sync_papers")
                    .orElse(com.sra.journal_tracking.entity.jpa.SystemConfig.builder()
                            .configKey("last_auto_sync_papers").build());
            papersConfig.setConfigValue(String.valueOf(totalPapers));
            systemConfigRepository.save(papersConfig);

            var timeConfig = systemConfigRepository.findByConfigKey("last_auto_sync_time")
                    .orElse(com.sra.journal_tracking.entity.jpa.SystemConfig.builder()
                            .configKey("last_auto_sync_time").build());
            timeConfig.setConfigValue(time.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            systemConfigRepository.save(timeConfig);
        } catch (Exception e) {
            log.warn("Failed to save auto-sync result: {}", e.getMessage());
        }
    }

    public Map<String, Object> getAutoSyncStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("enabled", isAutoSyncEnabled());
        status.put("lastPapersCount",
                systemConfigRepository.findByConfigKey("last_auto_sync_papers")
                        .map(c -> {
                            try { return Integer.parseInt(c.getConfigValue()); }
                            catch (NumberFormatException e) { return 0; }
                        }).orElse(0));
        status.put("lastSyncTime",
                systemConfigRepository.findByConfigKey("last_auto_sync_time")
                        .map(c -> c.getConfigValue()).orElse(null));
        return status;
    }
}
