package com.sra.journal_tracking.service;

import com.sra.journal_tracking.entity.jpa.Journal;
import com.sra.journal_tracking.repository.jpa.JournalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Fetches journal quartile & SJR data from the free SCImago Journal Rank CSV.
 * No API key required — SCImago provides a public CSV download.
 *
 * Matching strategy (in order):
 *   1. Exact ISSN match (preferred — most reliable)
 *   2. Normalized name match (fallback)
 *
 * Scheduled to run on the 1st of each month at 3 AM.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JournalEnrichmentService {

    private static final String SCIMAGO_CSV_URL =
            "https://www.scimagojr.com/journalrank.php?out=csv";
    private static final int HTTP_TIMEOUT_SECONDS = 120;
    private static final int BATCH_SIZE = 500;

    private final JournalRepository journalRepository;

    @Value("${app.scimago.enabled:true}")
    private boolean enabled;

    /**
     * Manual trigger — admin can call this to refresh quartile data on demand.
     * @return summary string (matched / total)
     */
    @Transactional
    public String enrichJournals() {
        if (!enabled) {
            log.info("SCImago enrichment is disabled (app.scimago.enabled=false)");
            return "SCImago enrichment disabled";
        }

        log.info("Starting SCImago journal enrichment...");
        long start = System.currentTimeMillis();

        // Step 1: Download & parse CSV
        Map<String, ScimagoEntry> byIssn = new HashMap<>();
        Map<String, ScimagoEntry> byName = new HashMap<>();
        int csvRows = downloadAndParse(byIssn, byName);

        if (csvRows == 0) {
            log.warn("SCImago CSV returned 0 rows — aborting enrichment");
            return "Failed: SCImago CSV empty";
        }

        log.info("Parsed {} SCImago entries ({} with ISSN, {} name-only)",
                csvRows, byIssn.size(), byName.size());

        // Step 2: Match & update journals in batches
        int matched = 0;
        int updated = 0;
        int page = 0;

        while (true) {
            List<Journal> batch = journalRepository.findAllByOrderByJournalNameAsc(
                    PageRequest.of(page, BATCH_SIZE)).getContent();
            if (batch.isEmpty()) break;

            for (Journal journal : batch) {
                ScimagoEntry entry = matchJournal(journal, byIssn, byName);
                if (entry != null) {
                    matched++;
                    boolean changed = applyEnrichment(journal, entry);
                    if (changed) updated++;
                }
            }

            journalRepository.saveAll(batch);
            page++;
        }

        long elapsed = System.currentTimeMillis() - start;
        String summary = String.format(
                "SCImago enrichment complete: %d journals scanned, %d matched, %d updated in %dms",
                page * BATCH_SIZE, matched, updated, elapsed);
        log.info(summary);
        return summary;
    }

    /**
     * Scheduled job: 3 AM on the 1st of every month.
     */
    @Scheduled(cron = "${app.scimago.cron:0 0 3 1 * ?}")
    public void scheduledEnrichment() {
        log.info("Scheduled SCImago enrichment triggered");
        try {
            String result = enrichJournals();
            log.info("Scheduled SCImago enrichment result: {}", result);
        } catch (Exception e) {
            log.error("Scheduled SCImago enrichment failed", e);
        }
    }

    // ── CSV download & parse ──

    private int downloadAndParse(Map<String, ScimagoEntry> byIssn,
                                 Map<String, ScimagoEntry> byName) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(SCIMAGO_CSV_URL))
                    .timeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
                    .header("User-Agent", "SCITRACK/1.0 (Academic Research Tool)")
                    .GET()
                    .build();

            HttpResponse<java.io.InputStream> response = client.send(request,
                    HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                log.error("SCImago returned HTTP {}", response.statusCode());
                return 0;
            }

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body()));

            // Skip header line
            String header = reader.readLine();
            if (header == null) return 0;

            int count = 0;
            String line;
            while ((line = reader.readLine()) != null) {
                ScimagoEntry entry = parseLine(line);
                if (entry == null) continue;
                count++;

                if (entry.issn != null && !entry.issn.isBlank()) {
                    byIssn.put(entry.issn.trim(), entry);
                    // Also index by name for fallback
                    byName.put(entry.title.trim().toLowerCase(), entry);
                } else {
                    byName.put(entry.title.trim().toLowerCase(), entry);
                }
            }

            return count;
        } catch (Exception e) {
            log.error("Failed to download/parse SCImago CSV: {}", e.getMessage(), e);
            return 0;
        }
    }

    /**
     * Parse one CSV line from SCImago.
     * CSV format (17 columns):
     *   Rank, Title, ISSN, SJR, H index, Total Docs (2024), Total Docs (3years),
     *   Total Refs, Total Cites (3years), Citable Docs (3years), Cites/Doc (2years),
     *   Ref/Doc, Country, Region, Publisher, Coverage, Categories, Quartile
     */
    private ScimagoEntry parseLine(String line) {
        // SCImago CSV may contain commas inside quoted fields — use a simple state-machine
        List<String> fields = parseCsvLine(line);
        if (fields.size() < 18) return null;

        try {
            String title = fields.get(1).replace("\"", "").trim();
            String issn = fields.get(2).replace("\"", "").trim();
            String sjrStr = fields.get(3).replace("\"", "").trim();
            String quartile = fields.get(17).replace("\"", "").trim();

            BigDecimal sjr = null;
            if (!sjrStr.isEmpty()) {
                sjr = new BigDecimal(sjrStr);
            }

            // ISSN may contain multiple ISSNs separated by comma/space
            String primaryIssn = issn.split("[,\\s]+")[0].trim();

            return new ScimagoEntry(title, primaryIssn, sjr, quartile);
        } catch (Exception e) {
            log.trace("Failed to parse SCImago line: {}", e.getMessage());
            return null;
        }
    }

    /** Parse CSV line handling quoted fields with embedded commas. */
    private List<String> parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        result.add(current.toString());
        return result;
    }

    // ── Matching ──

    private ScimagoEntry matchJournal(Journal journal,
                                      Map<String, ScimagoEntry> byIssn,
                                      Map<String, ScimagoEntry> byName) {
        // Strategy 1: Match by ISSN
        if (journal.getIssn() != null && !journal.getIssn().isBlank()) {
            ScimagoEntry entry = byIssn.get(journal.getIssn().trim());
            if (entry != null) return entry;
        }

        // Strategy 2: Match by normalized name
        if (journal.getJournalName() != null) {
            String normalized = journal.getJournalName().trim().toLowerCase();
            return byName.get(normalized);
        }

        return null;
    }

    // ── Update ──

    private boolean applyEnrichment(Journal journal, ScimagoEntry entry) {
        boolean changed = false;

        // Update quartile if missing or outdated
        if (entry.quartile != null && !entry.quartile.isBlank()
                && !entry.quartile.equalsIgnoreCase(journal.getQuartile())) {
            journal.setQuartile(entry.quartile.toUpperCase());
            changed = true;
        }

        // Update SJR as impactFactor if we have a value and DB doesn't
        if (entry.sjr != null
                && (journal.getImpactFactor() == null
                    || journal.getImpactFactor().compareTo(BigDecimal.ZERO) == 0)) {
            journal.setImpactFactor(entry.sjr);
            changed = true;
        }

        return changed;
    }

    // ── Data class ──

    private record ScimagoEntry(String title, String issn, BigDecimal sjr, String quartile) {}
}
