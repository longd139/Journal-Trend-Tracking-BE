package com.sra.journal_tracking.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sra.journal_tracking.dto.author.OpenAlexAuthorResponseDTO;
import com.sra.journal_tracking.dto.author.SuggestedAuthorResponse;
import com.sra.journal_tracking.entity.jpa.Author;
import com.sra.journal_tracking.repository.jpa.AuthorRepository;
import com.sra.journal_tracking.service.AuthorSuggestionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AuthorSuggestionServiceImpl implements AuthorSuggestionService {

    private final AuthorRepository authorRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.openalex-email:}")
    private String openalexEmail;

    @Value("${app.openalex-api-key:}")
    private String openalexApiKey;

    private static final int CANDIDATE_COUNT = 20;
    private static final int SUGGESTED_LIMIT = 12;
    private static final int MAX_RETRIES = 3;

    public AuthorSuggestionServiceImpl(AuthorRepository authorRepository,
                                        RestTemplate restTemplate,
                                        ObjectMapper objectMapper) {
        this.authorRepository = authorRepository;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /** Pre-warm cache on startup — async, không block app. */
    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void warmUpCache() {
        try {
            Thread.sleep(3000); // chờ connection pool
            log.info("Pre-warming suggested authors cache...");
            getSuggestedAuthors();
            log.info("Suggested authors cache warmed up");
        } catch (Exception e) {
            log.warn("Failed to pre-warm suggested authors cache: {}", e.getMessage());
        }
    }

    @Override
    @Cacheable(value = "search:suggestedAuthors", cacheManager = "searchCacheManager",
               key = "'batchOpenAlexTop12'", unless = "#result == null || #result.isEmpty()")
    public List<SuggestedAuthorResponse> getSuggestedAuthors() {
        log.info("Fetching fresh suggested authors via OpenAlex batch API");

        // ── Step 1: Select candidates from DB (top authors by paper count) ──
        List<Author> candidates = authorRepository.findTopAuthorsByPaperCount(
                PageRequest.of(0, CANDIDATE_COUNT));

        String authParam = (openalexApiKey != null && !openalexApiKey.isBlank())
                ? "&api_key=" + openalexApiKey
                : (openalexEmail != null && !openalexEmail.isBlank())
                ? "&mailto=" + openalexEmail : "";

        String url;
        if (candidates.isEmpty()) {
            // ── Fallback: DB has no data → query OpenAlex directly for top-cited authors ──
            log.info("No authors with papers in DB, falling back to direct OpenAlex top-cited query");
            url = "https://api.openalex.org/authors?sort=cited_by_count:desc&per-page="
                    + SUGGESTED_LIMIT + authParam;
        } else {
            log.info("Selected {} candidates from DB (top by paper count)", candidates.size());

            // ── Step 2: Extract short OpenAlex IDs ──
            String idFilter = candidates.stream()
                    .map(Author::getExternalAuthorId)
                    .map(AuthorSuggestionServiceImpl::extractShortId)
                    .collect(Collectors.joining("|"));

            // ── Step 3: Build batch OpenAlex URL ──
            url = "https://api.openalex.org/authors?filter=ids.openalex:" + idFilter
                    + "&sort=cited_by_count:desc&per-page=" + SUGGESTED_LIMIT + authParam;
            log.info("OpenAlex batch author call: {} candidates → {} IDs", candidates.size(),
                    idFilter.length() > 120 ? idFilter.substring(0, 120) + "..." : idFilter);
        }

        // ── Step 4: Call OpenAlex API with retry ──
        String rawJson = fetchRawWithRetry(url);
        if (rawJson == null) {
            log.warn("OpenAlex author fetch failed after {} retries", MAX_RETRIES);
            return Collections.emptyList();
        }

        List<OpenAlexAuthorResponseDTO.AuthorResult> apiResults;
        try {
            OpenAlexAuthorResponseDTO response = objectMapper.readValue(rawJson,
                    OpenAlexAuthorResponseDTO.class);
            apiResults = response.getResults();
        } catch (Exception e) {
            log.error("Failed to parse OpenAlex batch response: {}", e.getMessage());
            return Collections.emptyList();
        }

        if (apiResults == null || apiResults.isEmpty()) {
            log.warn("OpenAlex returned no authors");
            return Collections.emptyList();
        }

        log.info("OpenAlex returned {} authors", apiResults.size());

        // ── Step 5: Build response ──
        List<SuggestedAuthorResponse> result = buildSuggestedAuthors(apiResults);
        log.info("Suggested authors: {} entries ready (cached 7 days)", result.size());
        return result;
    }

    /**
     * Build SuggestedAuthorResponse list from OpenAlex API results.
     */
    private List<SuggestedAuthorResponse> buildSuggestedAuthors(
            List<OpenAlexAuthorResponseDTO.AuthorResult> apiResults) {
        List<SuggestedAuthorResponse> result = new ArrayList<>();
        for (OpenAlexAuthorResponseDTO.AuthorResult ar : apiResults) {
            // Prefer summary_stats.h_index over top-level h_index (more reliable)
            Integer hIndex = ar.getSummaryStats() != null && ar.getSummaryStats().getHIndex() != null
                    ? ar.getSummaryStats().getHIndex()
                    : ar.getHIndex();

            // Top field from topics
            String topField = null;
            if (ar.getTopics() != null && !ar.getTopics().isEmpty()) {
                var firstTopic = ar.getTopics().get(0);
                topField = firstTopic.getField() != null
                        ? firstTopic.getField().getDisplayName()
                        : firstTopic.getDisplayName();
            }

            // Affiliation from last_known_institution
            String affiliation = ar.getLastKnownInstitution() != null
                    ? ar.getLastKnownInstitution().getDisplayName()
                    : null;

            result.add(SuggestedAuthorResponse.builder()
                    .authorId(ar.getId())
                    .fullName(ar.getDisplayName())
                    .affiliation(affiliation)
                    .hIndex(hIndex)
                    .totalCitations(ar.getCitedByCount())
                    .topField(topField)
                    .topFieldId(null)
                    .paperCount(ar.getWorksCount() != null ? Long.valueOf(ar.getWorksCount()) : 0L)
                    .build());
        }
        return result;
    }

    // ── Helpers ──

    /**
     * Extract short OpenAlex ID from full URL or return as-is if already short.
     * "https://openalex.org/A5023888391" → "A5023888391"
     * "A5023888391" → "A5023888391"
     */
    private static String extractShortId(String openAlexId) {
        if (openAlexId == null) return "";
        // Handle full URL: https://openalex.org/A5023888391
        int lastSlash = openAlexId.lastIndexOf('/');
        return lastSlash >= 0 ? openAlexId.substring(lastSlash + 1) : openAlexId;
    }

    /**
     * Retry logic — 3 attempts with 2s/4s/6s backoff.
     * Pattern reused from AuthorQuickStatsService.fetchRawWithRetry().
     */
    private String fetchRawWithRetry(String url) {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                log.debug("OpenAlex batch attempt {}/{}", attempt + 1, MAX_RETRIES);
                return restTemplate.getForObject(url, String.class);
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                log.warn("OpenAlex batch attempt {}/{} failed: {}",
                        attempt + 1, MAX_RETRIES, msg);

                if (attempt < MAX_RETRIES - 1) {
                    long waitMs = (attempt + 1) * 2000L;
                    log.warn("Retrying in {}ms...", waitMs);
                    try {
                        Thread.sleep(waitMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    log.error("OpenAlex batch exhausted all {} retries. URL: {}", MAX_RETRIES, url);
                }
            }
        }
        return null;
    }
}
