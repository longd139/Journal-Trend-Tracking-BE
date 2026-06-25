package com.sra.journal_tracking.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sra.journal_tracking.dto.author.AuthorQuickStatsResponse;
import com.sra.journal_tracking.dto.author.OpenAlexAuthorResponseDTO;
import com.sra.journal_tracking.exception.AppException;
import com.sra.journal_tracking.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;

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

    // ── URL builder ──

    private String buildUrl(String keyword) {
        // Pre-encode the search keyword to avoid double-encoding:
        // build().encode() would encode space → %20, then RestTemplate/URI
        // encodes % → %25, resulting in %2520 (OpenAlex sees literal "%20").
        // By pre-encoding and using build(true), the already-encoded %20
        // is recognized as a valid percent-sequence and left intact.
        String encodedKeyword = URLEncoder.encode(keyword, StandardCharsets.UTF_8);

        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(OPEN_ALEX_AUTHORS_URL)
                .queryParam("search", encodedKeyword)
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
