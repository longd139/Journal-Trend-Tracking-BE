package com.sra.journal_tracking.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sra.journal_tracking.dto.author.AuthorQuickStatsResponse;
import com.sra.journal_tracking.dto.author.AuthorResearchFocusResponse;
import com.sra.journal_tracking.dto.author.AuthorTimelineResponse;
import com.sra.journal_tracking.dto.author.CoAuthorResponse;
import com.sra.journal_tracking.dto.author.OpenAlexAuthorResponseDTO;
import com.sra.journal_tracking.dto.author.OpenAlexWorksResponseDTO;
import com.sra.journal_tracking.exception.AppException;
import com.sra.journal_tracking.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service that provides quick author statistics by calling the OpenAlex /authors API.
 * This is a real-time lookup — no local caching or database writes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthorQuickStatsService {

    private static final String OPEN_ALEX_AUTHORS_URL = "https://api.openalex.org/authors";
    private static final int MAX_RESULTS = 5;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.openalex-email:}")
    private String openalexEmail;

    /**
     * Search for an author by name and return their quick stats.
     *
     * @param keyword author name to search for
     * @return AuthorQuickStatsResponse with stats
     * @throws AppException(ErrorCode.AUTHOR_NOT_FOUND) if no author matches
     * @throws AppException(ErrorCode.EXTERNAL_API_ERROR) if OpenAlex API is unreachable
     */
    @Cacheable(value = "search:authorQuickStats", cacheManager = "searchCacheManager",
               key = "#keyword.trim().toLowerCase()")
    public AuthorQuickStatsResponse searchAuthor(String keyword) {
        String trimmedKeyword = keyword.trim();
        if (trimmedKeyword.isEmpty()) {
            throw new IllegalArgumentException("Author name cannot be empty");
        }

        String url = buildUrl(trimmedKeyword);
        log.info("Calling OpenAlex authors API for: '{}'", trimmedKeyword);
        log.info("OpenAlex URL: {}", url);

        // Single HTTP call — fetch raw JSON, then parse locally (avoids double rate-limit consumption)
        String rawJson = fetchRawWithRetry(url, trimmedKeyword);
        if (rawJson == null) {
            throw new AppException(ErrorCode.EXTERNAL_API_ERROR);
        }

        log.info("OpenAlex response: {} chars", rawJson.length());
        log.debug("OpenAlex raw (first 500): {}", rawJson.length() > 500
                ? rawJson.substring(0, 500)
                : rawJson);

        // Parse from the already-fetched string instead of making a second HTTP call
        OpenAlexAuthorResponseDTO response;
        try {
            response = objectMapper.readValue(rawJson, OpenAlexAuthorResponseDTO.class);
        } catch (Exception e) {
            log.error("Failed to parse OpenAlex response: {}", e.getMessage());
            throw new AppException(ErrorCode.EXTERNAL_API_ERROR);
        }

        if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
            log.warn("No authors found for: {} (meta.count={}, rawSnippet={})",
                    trimmedKeyword,
                    response != null && response.getMeta() != null ? response.getMeta().getCount() : "?",
                    rawJson.length() > 300 ? rawJson.substring(0, 300) : rawJson);
            throw new AppException(ErrorCode.AUTHOR_NOT_FOUND);
        }

        log.info("OpenAlex returned {} authors for '{}' (total count: {})",
                response.getResults().size(), trimmedKeyword,
                response.getMeta() != null ? response.getMeta().getCount() : "?");

        // Pick the best match — prefer exact display_name match, then by works_count descending
        OpenAlexAuthorResponseDTO.AuthorResult bestMatch = pickBestMatch(response.getResults(), trimmedKeyword);
        if (bestMatch == null) {
            throw new AppException(ErrorCode.AUTHOR_NOT_FOUND);
        }

        return mapToResponse(bestMatch);
    }

    /**
     * Get the yearly productivity &amp; impact timeline for an author.
     * Returns year-by-year breakdown of papers published (works_count) and
     * citations received (cited_by_count) — suitable for a Bar + Line chart.
     * <p>
     * Results are cached for 1 hour via Caffeine.
     *
     * @param keyword author name to search for
     * @return AuthorTimelineResponse with yearly data points
     * @throws AppException(ErrorCode.AUTHOR_NOT_FOUND) if no author matches
     * @throws AppException(ErrorCode.EXTERNAL_API_ERROR) if OpenAlex API is unreachable
     */
    @Cacheable(value = "search:authorTimeline", cacheManager = "searchCacheManager",
               key = "#keyword.trim().toLowerCase()", unless = "#result == null")
    public AuthorTimelineResponse getTimeline(String keyword) {
        String trimmedKeyword = keyword.trim();
        if (trimmedKeyword.isEmpty()) {
            throw new IllegalArgumentException("Author name cannot be empty");
        }

        // ── 1. Fetch author from OpenAlex ──
        String url = buildUrl(trimmedKeyword);
        log.info("Timeline: calling OpenAlex authors API for '{}'", trimmedKeyword);

        String rawJson = fetchRawWithRetry(url, trimmedKeyword);
        if (rawJson == null) {
            throw new AppException(ErrorCode.EXTERNAL_API_ERROR);
        }

        OpenAlexAuthorResponseDTO response;
        try {
            response = objectMapper.readValue(rawJson, OpenAlexAuthorResponseDTO.class);
        } catch (Exception e) {
            log.error("Timeline: failed to parse OpenAlex response: {}", e.getMessage());
            throw new AppException(ErrorCode.EXTERNAL_API_ERROR);
        }

        if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
            log.warn("Timeline: no authors found for '{}'", trimmedKeyword);
            throw new AppException(ErrorCode.AUTHOR_NOT_FOUND);
        }

        // ── 2. Pick best match ──
        OpenAlexAuthorResponseDTO.AuthorResult bestMatch = pickBestMatch(response.getResults(), trimmedKeyword);
        if (bestMatch == null) {
            throw new AppException(ErrorCode.AUTHOR_NOT_FOUND);
        }

        log.info("Timeline: best match = {} (works={}, citations={})",
                bestMatch.getDisplayName(),
                bestMatch.getWorksCount(),
                bestMatch.getCitedByCount());

        // ── 3. Map counts_by_year → timeline ──
        List<AuthorTimelineResponse.YearlyPoint> timeline;
        if (bestMatch.getCountsByYear() == null || bestMatch.getCountsByYear().isEmpty()) {
            timeline = new ArrayList<>();
            log.warn("Timeline: no counts_by_year data for '{}'", bestMatch.getDisplayName());
        } else {
            timeline = bestMatch.getCountsByYear().stream()
                    .filter(y -> y.getYear() != null)
                    .map(y -> AuthorTimelineResponse.YearlyPoint.builder()
                            .year(y.getYear())
                            .worksCount(y.getWorksCount() != null ? y.getWorksCount() : 0)
                            .citedByCount(y.getCitedByCount() != null ? y.getCitedByCount() : 0)
                            .build())
                    .collect(Collectors.toList());
        }

        Integer hIndex = bestMatch.getSummaryStats() != null && bestMatch.getSummaryStats().getHIndex() != null
                ? bestMatch.getSummaryStats().getHIndex()
                : bestMatch.getHIndex();

        return AuthorTimelineResponse.builder()
                .fullName(bestMatch.getDisplayName())
                .openAlexId(bestMatch.getId())
                .totalPapers(bestMatch.getWorksCount())
                .totalCitations(bestMatch.getCitedByCount())
                .hIndex(hIndex)
                .timeline(timeline)
                .build();
    }

    /**
     * Get an author's research focus — top topics/keywords with paper counts
     * suitable for a Pie Chart or Treemap visualization.
     * <p>
     * Results are cached for 1 hour via Caffeine.
     *
     * @param keyword author name to search for
     * @return AuthorResearchFocusResponse with top topics sorted by count
     * @throws AppException(ErrorCode.AUTHOR_NOT_FOUND) if no author matches
     * @throws AppException(ErrorCode.EXTERNAL_API_ERROR) if OpenAlex API is unreachable
     */
    @Cacheable(value = "search:authorResearchFocus", cacheManager = "searchCacheManager",
               key = "#keyword.trim().toLowerCase()", unless = "#result == null")
    public AuthorResearchFocusResponse getResearchFocus(String keyword) {
        String trimmedKeyword = keyword.trim();
        if (trimmedKeyword.isEmpty()) {
            throw new IllegalArgumentException("Author name cannot be empty");
        }

        // ── 1. Fetch author from OpenAlex ──
        String url = buildUrl(trimmedKeyword);
        log.info("ResearchFocus: calling OpenAlex authors API for '{}'", trimmedKeyword);

        String rawJson = fetchRawWithRetry(url, trimmedKeyword);
        if (rawJson == null) {
            throw new AppException(ErrorCode.EXTERNAL_API_ERROR);
        }

        OpenAlexAuthorResponseDTO response;
        try {
            response = objectMapper.readValue(rawJson, OpenAlexAuthorResponseDTO.class);
        } catch (Exception e) {
            log.error("ResearchFocus: failed to parse OpenAlex response: {}", e.getMessage());
            throw new AppException(ErrorCode.EXTERNAL_API_ERROR);
        }

        if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
            log.warn("ResearchFocus: no authors found for '{}'", trimmedKeyword);
            throw new AppException(ErrorCode.AUTHOR_NOT_FOUND);
        }

        // ── 2. Pick best match ──
        OpenAlexAuthorResponseDTO.AuthorResult bestMatch = pickBestMatch(response.getResults(), trimmedKeyword);
        if (bestMatch == null) {
            throw new AppException(ErrorCode.AUTHOR_NOT_FOUND);
        }

        log.info("ResearchFocus: best match = {} (topics={})",
                bestMatch.getDisplayName(),
                bestMatch.getTopics() != null ? bestMatch.getTopics().size() : 0);

        // ── 3. Map topics → research focus ──
        List<AuthorResearchFocusResponse.TopicFocus> topicList = new ArrayList<>();
        if (bestMatch.getTopics() != null && !bestMatch.getTopics().isEmpty()) {
            int totalWorks = bestMatch.getWorksCount() != null ? bestMatch.getWorksCount() : 1;

            topicList = bestMatch.getTopics().stream()
                    .filter(t -> t.getDisplayName() != null)
                    .map(t -> {
                        int count = t.getCount() != null ? t.getCount() : 0;
                        double pct = totalWorks > 0 ? (count * 100.0) / totalWorks : 0;

                        return AuthorResearchFocusResponse.TopicFocus.builder()
                                .topicName(t.getDisplayName())
                                .paperCount(count)
                                .percentage(Math.round(pct * 10.0) / 10.0) // 1 decimal place
                                .subfield(t.getSubfield() != null ? t.getSubfield().getDisplayName() : null)
                                .field(t.getField() != null ? t.getField().getDisplayName() : null)
                                .domain(t.getDomain() != null ? t.getDomain().getDisplayName() : null)
                                .build();
                    })
                    .sorted(Comparator.comparingInt(AuthorResearchFocusResponse.TopicFocus::getPaperCount).reversed())
                    .collect(Collectors.toList());
        }

        return AuthorResearchFocusResponse.builder()
                .fullName(bestMatch.getDisplayName())
                .openAlexId(bestMatch.getId())
                .totalPapers(bestMatch.getWorksCount())
                .totalTopics(topicList.size())
                .topics(topicList)
                .build();
    }

    /**
     * Get an author's top co-authors (collaboration network).
     * Fetches the author's most-cited works from OpenAlex, aggregates
     * co-author frequencies from the authorships, and returns the top 10.
     * <p>
     * Results are cached for 1 hour via Caffeine.
     *
     * @param keyword author name to search for
     * @return CoAuthorResponse with top co-authors
     * @throws AppException(ErrorCode.AUTHOR_NOT_FOUND) if no author matches
     * @throws AppException(ErrorCode.EXTERNAL_API_ERROR) if OpenAlex API is unreachable
     */
    @Cacheable(value = "search:authorCoAuthors", cacheManager = "searchCacheManager",
               key = "#keyword.trim().toLowerCase()", unless = "#result == null")
    public CoAuthorResponse getCoAuthors(String keyword) {
        String trimmedKeyword = keyword.trim();
        if (trimmedKeyword.isEmpty()) {
            throw new IllegalArgumentException("Author name cannot be empty");
        }

        // ── 1. Find the author first ──
        String url = buildUrl(trimmedKeyword);
        log.info("CoAuthors: finding author '{}'", trimmedKeyword);

        String rawJson = fetchRawWithRetry(url, trimmedKeyword);
        if (rawJson == null) {
            throw new AppException(ErrorCode.EXTERNAL_API_ERROR);
        }

        OpenAlexAuthorResponseDTO response;
        try {
            response = objectMapper.readValue(rawJson, OpenAlexAuthorResponseDTO.class);
        } catch (Exception e) {
            log.error("CoAuthors: failed to parse author response: {}", e.getMessage());
            throw new AppException(ErrorCode.EXTERNAL_API_ERROR);
        }

        if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
            throw new AppException(ErrorCode.AUTHOR_NOT_FOUND);
        }

        OpenAlexAuthorResponseDTO.AuthorResult bestMatch = pickBestMatch(response.getResults(), trimmedKeyword);
        if (bestMatch == null) {
            throw new AppException(ErrorCode.AUTHOR_NOT_FOUND);
        }

        String authorId = bestMatch.getId();
        String shortId = extractShortId(authorId);
        log.info("CoAuthors: best match = {} (id={})", bestMatch.getDisplayName(), shortId);

        // ── 2. Fetch the author's works (top 200 by citations) ──
        String worksUrl = UriComponentsBuilder
                .fromHttpUrl("https://api.openalex.org/works")
                .queryParam("filter", "authorships.author.id:" + shortId)
                .queryParam("per-page", 200)
                .queryParam("sort", "cited_by_count:desc")
                .build()
                .encode()
                .toUriString();

        log.info("CoAuthors: fetching works from {}", worksUrl);

        String worksRawJson = fetchRawWithRetry(worksUrl, trimmedKeyword);
        if (worksRawJson == null) {
            throw new AppException(ErrorCode.EXTERNAL_API_ERROR);
        }

        OpenAlexWorksResponseDTO worksResponse;
        try {
            worksResponse = objectMapper.readValue(worksRawJson, OpenAlexWorksResponseDTO.class);
        } catch (Exception e) {
            log.error("CoAuthors: failed to parse works response: {}", e.getMessage());
            throw new AppException(ErrorCode.EXTERNAL_API_ERROR);
        }

        if (worksResponse == null || worksResponse.getResults() == null) {
            log.warn("CoAuthors: no works found for {}", bestMatch.getDisplayName());
            return CoAuthorResponse.builder()
                    .fullName(bestMatch.getDisplayName())
                    .openAlexId(authorId)
                    .totalPapersAnalyzed(0)
                    .totalCoAuthors(0)
                    .coAuthors(new ArrayList<>())
                    .build();
        }

        // ── 3. Aggregate co-author frequencies ──
        Map<String, CoAuthorAggregate> coAuthorMap = new LinkedHashMap<>();

        for (OpenAlexWorksResponseDTO.WorkResult work : worksResponse.getResults()) {
            if (work.getAuthorships() == null) continue;

            for (OpenAlexWorksResponseDTO.Authorship authorship : work.getAuthorships()) {
                if (authorship.getAuthor() == null) continue;
                String coId = authorship.getAuthor().getId();
                if (coId == null || coId.equals(authorId)) continue; // skip self

                CoAuthorAggregate agg = coAuthorMap.computeIfAbsent(coId, k -> {
                    CoAuthorAggregate a = new CoAuthorAggregate();
                    a.name = authorship.getAuthor().getDisplayName();
                    a.openAlexId = coId;
                    return a;
                });
                agg.collaborationCount++;

                // Capture first non-null institution
                if (agg.lastInstitution == null
                        && authorship.getInstitutions() != null
                        && !authorship.getInstitutions().isEmpty()
                        && authorship.getInstitutions().get(0).getDisplayName() != null) {
                    agg.lastInstitution = authorship.getInstitutions().get(0).getDisplayName();
                }
            }
        }

        // ── 4. Sort by collaboration count desc, take top 10 ──
        List<CoAuthorResponse.CoAuthorEntry> entries = coAuthorMap.values().stream()
                .sorted(Comparator.comparingInt(CoAuthorAggregate::getCollaborationCount).reversed())
                .limit(10)
                .map(agg -> CoAuthorResponse.CoAuthorEntry.builder()
                        .name(agg.name)
                        .openAlexId(agg.openAlexId)
                        .collaborationCount(agg.collaborationCount)
                        .lastInstitution(agg.lastInstitution)
                        .build())
                .collect(Collectors.toList());

        log.info("CoAuthors: found {} unique co-authors for '{}', returning top {}",
                coAuthorMap.size(), bestMatch.getDisplayName(), entries.size());

        return CoAuthorResponse.builder()
                .fullName(bestMatch.getDisplayName())
                .openAlexId(authorId)
                .totalPapersAnalyzed(worksResponse.getResults().size())
                .totalCoAuthors(coAuthorMap.size())
                .coAuthors(entries)
                .build();
    }

    /**
     * Extract the short ID from an OpenAlex URL (e.g. "https://openalex.org/A5112456378" → "A5112456378").
     */
    private String extractShortId(String openAlexUrl) {
        if (openAlexUrl == null) return null;
        int lastSlash = openAlexUrl.lastIndexOf('/');
        return lastSlash >= 0 ? openAlexUrl.substring(lastSlash + 1) : openAlexUrl;
    }

    /** Mutable aggregate for co-author counting (used during stream processing). */
    private static class CoAuthorAggregate {
        String name;
        String openAlexId;
        int collaborationCount;
        String lastInstitution;

        int getCollaborationCount() { return collaborationCount; }
    }

    // ── URL builder ──

    private String buildUrl(String keyword) {
        // Use filter=display_name.search: instead of the generic search= param.
        // The generic /authors search parser treats dots in initials (e.g. "O.",
        // "M." in "Ahmed O. M. Bahageel") as regex-like operators, causing
        // extremely broad scans and OpenAlex 504 "query_timeout".
        // display_name.search: does a targeted substring match against the
        // display_name field and handles dots/special chars correctly.
        //
        // Pre-encode to avoid double-encoding: build(true) recognizes already-
        // encoded sequences (%XX) and leaves them intact.
        String filterValue = "display_name.search:" + keyword;
        String encodedFilter = URLEncoder.encode(filterValue, StandardCharsets.UTF_8);

        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(OPEN_ALEX_AUTHORS_URL)
                .queryParam("filter", encodedFilter)
                .queryParam("per-page", MAX_RESULTS);

        if (openalexEmail != null && !openalexEmail.isBlank()) {
            builder.queryParam("mailto", openalexEmail);
        }

        return builder.build(true).toUriString();
    }

    // ── Best match selection ──

    private OpenAlexAuthorResponseDTO.AuthorResult pickBestMatch(
            List<OpenAlexAuthorResponseDTO.AuthorResult> results,
            String keyword) {

        String normalizedKeyword = normalize(keyword);

        // Try exact match on display name first
        for (OpenAlexAuthorResponseDTO.AuthorResult result : results) {
            if (result.getDisplayName() != null
                    && normalize(result.getDisplayName()).equals(normalizedKeyword)) {
                return result;
            }
        }

        // Try case-insensitive contains
        for (OpenAlexAuthorResponseDTO.AuthorResult result : results) {
            if (result.getDisplayName() != null
                    && normalize(result.getDisplayName()).contains(normalizedKeyword)) {
                return result;
            }
        }

        // Fallback: pick the one with the most works (most established author)
        return results.stream()
                .max(Comparator.comparing(
                        r -> r.getWorksCount() != null ? r.getWorksCount() : 0))
                .orElse(results.get(0));
    }

    // ── Mapping ──

    private AuthorQuickStatsResponse mapToResponse(OpenAlexAuthorResponseDTO.AuthorResult result) {
        // Prefer summary_stats.h_index over top-level h_index (if both exist)
        Integer hIndex = result.getSummaryStats() != null && result.getSummaryStats().getHIndex() != null
                ? result.getSummaryStats().getHIndex()
                : result.getHIndex();

        Integer i10Index = result.getSummaryStats() != null && result.getSummaryStats().getI10Index() != null
                ? result.getSummaryStats().getI10Index()
                : result.getI10Index();

        Double twoYearMeanCitedness = result.getSummaryStats() != null
                ? result.getSummaryStats().getTwoYearMeanCitedness()
                : null;

        String affiliation = result.getLastKnownInstitution() != null
                ? result.getLastKnownInstitution().getDisplayName()
                : null;

        // Derive a human-readable institution type label
        String academicTitle = deriveAcademicTitle(result);

        return AuthorQuickStatsResponse.builder()
                .fullName(result.getDisplayName())
                .academicTitle(academicTitle)
                .currentAffiliation(affiliation)
                .totalPapers(result.getWorksCount())
                .totalCitations(result.getCitedByCount())
                .hIndex(hIndex)
                .i10Index(i10Index)
                .twoYearMeanCitedness(twoYearMeanCitedness)
                .orcid(normalizeOrcid(result.getOrcid()))
                .openAlexId(result.getId())
                .build();
    }

    /**
     * Derive an academic title/type from the institution type.
     * OpenAlex doesn't provide explicit academic degrees, but we can
     * infer context from the institution type (education → likely a professor/researcher).
     */
    private String deriveAcademicTitle(OpenAlexAuthorResponseDTO.AuthorResult result) {
        if (result.getLastKnownInstitution() == null
                || result.getLastKnownInstitution().getType() == null) {
            return null;
        }

        String instType = result.getLastKnownInstitution().getType().toLowerCase();
        return switch (instType) {
            case "education" -> "Researcher / Faculty";
            case "government" -> "Government Researcher";
            case "nonprofit" -> "Nonprofit Researcher";
            case "company" -> "Industry Researcher";
            case "healthcare" -> "Healthcare Researcher";
            default -> "Researcher";
        };
    }

    // ── Retry logic — fetches raw JSON string for debugging ──

    private String fetchRawWithRetry(String url, String context) {
        int maxRetries = 3;
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                log.debug("OpenAlex authors API call attempt {}/{} for '{}'", attempt + 1, maxRetries, context);
                return restTemplate.getForObject(url, String.class);
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                log.warn("OpenAlex authors API attempt {}/{} failed for '{}': {}",
                        attempt + 1, maxRetries, context, msg);

                if (attempt < maxRetries - 1) {
                    long waitMs = (attempt + 1) * 2000L;
                    log.warn("Retrying in {}ms...", waitMs);
                    try {
                        Thread.sleep(waitMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    log.error("OpenAlex authors API exhausted all {} retries for '{}'. URL: {}",
                            maxRetries, context, url);
                }
            }
        }
        return null;
    }

    // ── Helpers ──

    private String normalize(String value) {
        if (value == null) return "";
        return value.toLowerCase().trim().replaceAll("\\s+", " ");
    }

    private String normalizeOrcid(String orcid) {
        if (orcid == null || orcid.isBlank()) return null;
        // Strip ORCID URL prefix if present
        return orcid.replace("https://orcid.org/", "").trim();
    }
}
