package com.sra.journal_tracking.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchBackfillService {
    private static final Duration BACKFILL_COOLDOWN = Duration.ofMinutes(2);
    private static final int MAX_BACKFILL_TERMS = 3;

    private final DataSyncService dataSyncService;
    private final KeywordExpansionService keywordExpansionService;
    private final Map<String, Instant> recentBackfills = new ConcurrentHashMap<>();

    public boolean requestBackfill(String query, int limit) {
        String normalizedQuery = keywordExpansionService.normalize(query);
        if (normalizedQuery.isBlank()) {
            return false;
        }

        Instant now = Instant.now();
        cleanupExpired(now);

        Instant previous = recentBackfills.putIfAbsent(normalizedQuery, now);
        if (previous != null && previous.plus(BACKFILL_COOLDOWN).isAfter(now)) {
            log.debug("Search backfill skipped for '{}': recently requested", query);
            return true;
        }
        if (previous != null) {
            recentBackfills.put(normalizedQuery, now);
        }

        int safeLimit = Math.min(25, Math.max(10, limit * 3));
        List<String> terms = keywordExpansionService.expand(query, MAX_BACKFILL_TERMS);
        for (String term : terms) {
            dataSyncService.syncFromOpenAlexAsync(term, safeLimit);
        }
        log.info("Search backfill queued for '{}' using {} term(s)", query, terms.size());
        return true;
    }

    private void cleanupExpired(Instant now) {
        recentBackfills.entrySet().removeIf(entry -> entry.getValue().plus(BACKFILL_COOLDOWN).isBefore(now));
    }
}
