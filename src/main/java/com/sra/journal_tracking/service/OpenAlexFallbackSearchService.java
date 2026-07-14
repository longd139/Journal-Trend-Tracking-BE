package com.sra.journal_tracking.service;

import com.sra.journal_tracking.dto.paper.AuthorDTO;
import com.sra.journal_tracking.dto.paper.KeywordDTO;
import com.sra.journal_tracking.dto.paper.PaperDetailResponseDTO;
import com.sra.journal_tracking.dto.sync.OpenAlexResponseDTO;
import com.sra.journal_tracking.service.DataSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAlexFallbackSearchService {
    private static final String OPEN_ALEX_BASE_URL = "https://api.openalex.org";
    private static final int RECENT_PUBLICATION_YEAR_WINDOW = 3;
    private static final int MAX_AUTHORS = 5;
    private static final int MAX_KEYWORDS = 8;

    private final RestTemplate restTemplate;
    private final KeywordExpansionService keywordExpansionService;
    private final PaperCacheService paperCacheService;
    private final DataSyncService dataSyncService;

    // In-memory map: stablePreviewId UUID → OpenAlex work URL. No DB needed.
    private final ConcurrentHashMap<UUID, String> paperIdToWorkUrl = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Value("${app.openalex-api-key:}")
    private String openalexApiKey;

    @org.springframework.beans.factory.annotation.Value("${app.openalex-email:}")
    private String openalexEmail;

    /**
     * Fetch a single paper by local UUID — looks up OpenAlex work URL from in-memory cache.
     * Used by getPaperDetails when paper is not in local DB.
     */
    public PaperDetailResponseDTO getPaperByUuid(UUID paperId) {
        String workUrl = paperIdToWorkUrl.get(paperId);
        if (workUrl != null) {
            return getPaperByOpenAlexId(workUrl);
        }
        return null;
    }

    /**
     * Fetch a single paper from OpenAlex by its work ID URL (e.g. https://openalex.org/W123456).
     * No DB interaction — pure OpenAlex API call.
     */
    public PaperDetailResponseDTO getPaperByOpenAlexId(String openAlexWorkUrl) {
        String shortId = extractShortId(openAlexWorkUrl);
        if (shortId == null) return null;

        String url = withApiKey(UriComponentsBuilder
                .fromHttpUrl(OPEN_ALEX_BASE_URL + "/works/" + shortId)
                .queryParam("select", "id,doi,title,display_name,publication_year,publication_date,"
                        + "cited_by_count,abstract_inverted_index,open_access,"
                        + "primary_location,best_oa_location,topics,keywords,authorships"))
                .build().encode().toUriString();

        try {
            OpenAlexResponseDTO.OpenAlexWorkDTO work = restTemplate.getForObject(url, OpenAlexResponseDTO.OpenAlexWorkDTO.class);
            if (work == null) return null;
            String abstractText = rebuildAbstract(work.getAbstractInvertedIndex());
            return mapToPaper(work, abstractText);
        } catch (RestClientException e) {
            log.warn("OpenAlex fetch single work failed for '{}': {}", shortId, e.getMessage());
            return null;
        }
    }

    /**
     * Lightweight: fetch only citation count from OpenAlex for a given work URL.
     * Returns null if the call fails or the work is not found.
     */
    public Integer fetchCitationCount(String openAlexWorkUrl) {
        String shortId = extractShortId(openAlexWorkUrl);
        if (shortId == null) return null;

        String url = withApiKey(UriComponentsBuilder
                .fromHttpUrl(OPEN_ALEX_BASE_URL + "/works/" + shortId)
                .queryParam("select", "cited_by_count"))
                .build().encode().toUriString();

        try {
            OpenAlexResponseDTO.OpenAlexWorkDTO work =
                    restTemplate.getForObject(url, OpenAlexResponseDTO.OpenAlexWorkDTO.class);
            if (work == null) return null;
            return work.getCitedByCount();
        } catch (RestClientException e) {
            log.debug("OpenAlex citation count fetch failed for '{}': {}", shortId, e.getMessage());
            return null;
        }
    }

    /**
     * Lightweight: fetch citation count from OpenAlex by DOI.
     * Uses filter=doi: to find the work, then extracts cited_by_count.
     */
    public Integer fetchCitationCountByDoi(String doi) {
        if (doi == null || doi.isBlank()) return null;
        String doiUrl = doi.startsWith("http") ? doi : "https://doi.org/" + doi;

        String url = withApiKey(UriComponentsBuilder
                .fromHttpUrl(OPEN_ALEX_BASE_URL + "/works")
                .queryParam("filter", "doi:" + doiUrl)
                .queryParam("select", "cited_by_count")
                .queryParam("per-page", 1))
                .build().encode().toUriString();

        try {
            OpenAlexResponseDTO response =
                    restTemplate.getForObject(url, OpenAlexResponseDTO.class);
            if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
                return null;
            }
            return response.getResults().get(0).getCitedByCount();
        } catch (RestClientException e) {
            log.debug("OpenAlex citation count fetch by DOI failed for '{}': {}", doi, e.getMessage());
            return null;
        }
    }

    /**
     * Search top cited papers by keyword — all time, sorted by citation count.
     */
    public List<PaperDetailResponseDTO> searchTopCited(String query, int size) {
        return searchTopCited(query, size, null, null);
    }

    /**
     * Search top cited papers by keyword with optional year filter.
     *
     * @param yearFrom optional: filter papers published from this year (inclusive)
     * @param yearTo   optional: filter papers published to this year (inclusive)
     */
    public List<PaperDetailResponseDTO> searchTopCited(String query, int size, Integer yearFrom, Integer yearTo) {
        String normalizedQuery = normalizeOpenAlexSearchQuery(query);
        if (normalizedQuery.isBlank()) return List.of();

        // Build OpenAlex filters — use fulltext.search filter with quotes for exact matching.
        // IMPORTANT: use build().toUriString() (no .encode()) because .encode() would turn
        // " and : inside the filter value into %22 and %3A, which OpenAlex cannot parse.
        List<String> filters = new ArrayList<>();
        filters.add("fulltext.search:" + quotedFilterValue(normalizedQuery));
        if (yearFrom != null) filters.add("from_publication_date:" + yearFrom + "-01-01");
        if (yearTo != null) filters.add("to_publication_date:" + yearTo + "-12-31");

        var builder = UriComponentsBuilder
                .fromHttpUrl(OPEN_ALEX_BASE_URL + "/works")
                .queryParam("filter", String.join(",", filters))
                .queryParam("sort", "cited_by_count:desc")
                .queryParam("per-page", size)
                .queryParam("select", "id,doi,title,display_name,publication_year,publication_date,"
                        + "cited_by_count,counts_by_year,abstract_inverted_index,open_access,"
                        + "primary_location,best_oa_location,topics,keywords,authorships");

        // No .encode() — the filter syntax (fulltext.search:"...") must pass through as-is
        String url = withApiKey(builder).build().toUriString();

        try {
            OpenAlexResponseDTO response = restTemplate.getForObject(url, OpenAlexResponseDTO.class);
            if (response == null || response.getResults() == null) return List.of();

            List<OpenAlexResponseDTO.OpenAlexWorkDTO> rawWorks = response.getResults().stream()
                    .limit(size)
                    .collect(Collectors.toList());

            // Fire-and-forget: async save to local DB (save-on-search)
            try {
                dataSyncService.saveWorksFromOpenAlexAsync(rawWorks);
            } catch (Exception e) {
                log.debug("Save-on-search dispatch failed: {}", e.getMessage());
            }

            return rawWorks.stream()
                    .map(work -> new WorkWithAbstract(work, rebuildAbstract(work.getAbstractInvertedIndex())))
                    .map(w -> mapToPaper(w.work(), w.abstractText()))
                    .collect(Collectors.toList());
        } catch (RestClientException e) {
            log.warn("OpenAlex top-cited search failed for '{}': {}", query, e.getMessage());
            return List.of();
        }
    }

    /**
     * Get total works count for a keyword from OpenAlex meta.
     * Uses fulltext.search filter for consistency with searchTopCited.
     * Returns 0 on failure or if the keyword returns no results.
     */
    public long getKeywordTotalCount(String keyword) {
        String normalizedQuery = normalizeOpenAlexSearchQuery(keyword);
        if (normalizedQuery.isBlank()) return 0;

        String url = withApiKey(UriComponentsBuilder
                .fromHttpUrl(OPEN_ALEX_BASE_URL + "/works")
                .queryParam("filter", "fulltext.search:" + quotedFilterValue(normalizedQuery))
                .queryParam("per-page", "1")
                .queryParam("select", "id"))
                .build().toUriString();

        try {
            OpenAlexResponseDTO response = restTemplate.getForObject(url, OpenAlexResponseDTO.class);
            if (response == null || response.getMeta() == null || response.getMeta().getCount() == null) {
                return 0;
            }
            log.info("OpenAlex total count for '{}': {}", keyword, response.getMeta().getCount());
            return response.getMeta().getCount();
        } catch (RestClientException e) {
            log.warn("OpenAlex count lookup failed for '{}': {}", keyword, e.getMessage());
            return 0;
        }
    }

    /**
     * Get yearly publication breakdown for a keyword from OpenAlex group_by.
     * Returns list of [year, count] pairs for years >= startYear, sorted by year ASC.
     */
    public List<OpenAlexYearlyCount> getKeywordYearlyBreakdown(String keyword, int startYear) {
        String normalizedQuery = normalizeOpenAlexSearchQuery(keyword);
        if (normalizedQuery.isBlank()) return List.of();

        String url = withApiKey(UriComponentsBuilder
                .fromHttpUrl(OPEN_ALEX_BASE_URL + "/works")
                .queryParam("filter",
                        "fulltext.search:" + quotedFilterValue(normalizedQuery)
                        + ",from_publication_date:" + startYear + "-01-01")
                .queryParam("group_by", "publication_year")
                .queryParam("per-page", "50"))
                .build().toUriString();

        try {
            OpenAlexGroupByResponse response = restTemplate.getForObject(url, OpenAlexGroupByResponse.class);
            if (response == null || response.getGroupBy() == null) return List.of();

            return response.getGroupBy().stream()
                    .map(g -> new OpenAlexYearlyCount(Integer.parseInt(g.getKey()), g.getCount()))
                    .sorted(java.util.Comparator.comparingInt(OpenAlexYearlyCount::year))
                    .toList();
        } catch (RestClientException e) {
            log.warn("OpenAlex yearly breakdown failed for '{}': {}", keyword, e.getMessage());
            return List.of();
        }
    }

    /**
     * Simple record for yearly publication counts from OpenAlex group_by.
     */
    public record OpenAlexYearlyCount(int year, long count) {}

    /**
     * Aggregates citation counts by year from the top-cited papers for a keyword.
     * Fetches raw OpenAlex works (up to 50), sums up counts_by_year across all papers,
     * and returns sorted yearly citation totals for years >= startYear.
     */
    public List<OpenAlexYearlyCount> getAggregatedCitationTrend(String keyword, int startYear) {
        String normalizedQuery = normalizeOpenAlexSearchQuery(keyword);
        if (normalizedQuery.isBlank()) return List.of();

        String url = withApiKey(UriComponentsBuilder
                .fromHttpUrl(OPEN_ALEX_BASE_URL + "/works")
                .queryParam("filter", "fulltext.search:" + quotedFilterValue(normalizedQuery))
                .queryParam("sort", "cited_by_count:desc")
                .queryParam("per-page", "50")
                .queryParam("select", "id,cited_by_count,counts_by_year"))
                .build().toUriString();

        try {
            OpenAlexResponseDTO response = restTemplate.getForObject(url, OpenAlexResponseDTO.class);
            if (response == null || response.getResults() == null) return List.of();

            // Aggregate citations by year across all fetched papers
            Map<Integer, Long> citationByYear = new java.util.TreeMap<>();
            for (var work : response.getResults()) {
                if (work.getCountsByYear() != null) {
                    for (var cy : work.getCountsByYear()) {
                        if (cy.getYear() != null && cy.getCitedByCount() != null && cy.getYear() >= startYear) {
                            citationByYear.merge(cy.getYear(), cy.getCitedByCount().longValue(), Long::sum);
                        }
                    }
                }
            }

            return citationByYear.entrySet().stream()
                    .map(e -> new OpenAlexYearlyCount(e.getKey(), e.getValue()))
                    .sorted(java.util.Comparator.comparingInt(OpenAlexYearlyCount::year))
                    .toList();
        } catch (RestClientException e) {
            log.warn("OpenAlex citation trend fetch failed for '{}': {}", keyword, e.getMessage());
            return List.of();
        }
    }

    /**
     * Search by relevance score — all time, no year filter.
     * Used by the orchestrator for fast keyword search via OpenAlex API.
     */
    public List<PaperDetailResponseDTO> searchNoYearFilter(String query, int size) {
        String normalizedQuery = normalizeOpenAlexSearchQuery(query);
        if (normalizedQuery.isBlank()) return List.of();

        int perPage = Math.min(25, Math.max(size * 2, 10));
        String url = withApiKey(UriComponentsBuilder
                .fromHttpUrl(OPEN_ALEX_BASE_URL + "/works")
                .queryParam("search", normalizedQuery)
                .queryParam("sort", "relevance_score:desc")
                .queryParam("per-page", perPage)
                .queryParam("select", "id,doi,title,display_name,publication_year,publication_date,cited_by_count,abstract_inverted_index,open_access,primary_location,best_oa_location,topics,keywords,authorships"))
                .build().encode().toUriString();

        try {
            OpenAlexResponseDTO response = restTemplate.getForObject(url, OpenAlexResponseDTO.class);
            if (response == null || response.getResults() == null) return List.of();

            List<OpenAlexResponseDTO.OpenAlexWorkDTO> rawWorks = response.getResults().stream()
                    .limit(size)
                    .collect(Collectors.toList());

            try {
                dataSyncService.saveWorksFromOpenAlexAsync(rawWorks);
            } catch (Exception e) {
                log.debug("Save-on-search dispatch failed: {}", e.getMessage());
            }

            return rawWorks.stream()
                    .map(work -> new WorkWithAbstract(work, rebuildAbstract(work.getAbstractInvertedIndex())))
                    .map(w -> mapToPaper(w.work(), w.abstractText()))
                    .collect(Collectors.toList());
        } catch (RestClientException e) {
            log.warn("OpenAlex relevance search failed for '{}': {}", query, e.getMessage());
            return List.of();
        }
    }

    public List<PaperDetailResponseDTO> search(String query, int size) {
        String normalizedQuery = normalizeOpenAlexSearchQuery(query);
        if (normalizedQuery.isBlank()) return List.of();

        LocalDate today = LocalDate.now();
        int startYear = Year.now().getValue() - RECENT_PUBLICATION_YEAR_WINDOW + 1;
        int perPage = Math.min(25, Math.max(size * 4, 10));

        String url = withApiKey(UriComponentsBuilder
                .fromHttpUrl(OPEN_ALEX_BASE_URL + "/works")
                .queryParam("search", normalizedQuery)
                .queryParam("filter", "from_publication_date:" + startYear + "-01-01,to_publication_date:" + today)
                .queryParam("sort", "relevance_score:desc")
                .queryParam("per-page", perPage)
                .queryParam("select", "id,doi,title,display_name,publication_year,publication_date,cited_by_count,abstract_inverted_index,open_access,primary_location,best_oa_location,topics,keywords,authorships"))
                .build().encode().toUriString();

        try {
            OpenAlexResponseDTO response = restTemplate.getForObject(url, OpenAlexResponseDTO.class);
            if (response == null || response.getResults() == null) return List.of();

            List<OpenAlexResponseDTO.OpenAlexWorkDTO> rawWorks = response.getResults().stream()
                    .filter(work -> isRecent(work, startYear, today))
                    .collect(Collectors.toList());

            // Async save all recent works (even those that fail local relevance check)
            try {
                dataSyncService.saveWorksFromOpenAlexAsync(rawWorks);
            } catch (Exception e) {
                log.debug("Save-on-search dispatch failed: {}", e.getMessage());
            }

            return rawWorks.stream()
                    .map(work -> new WorkWithAbstract(work, rebuildAbstract(work.getAbstractInvertedIndex())))
                    .filter(w -> isRelevant(w.work(), w.abstractText(), query))
                    .map(w -> mapToPaper(w.work(), w.abstractText()))
                    .limit(Math.max(1, size))
                    .collect(Collectors.toList());
        } catch (RestClientException e) {
            log.warn("OpenAlex fallback search failed for '{}': {}", query, e.getMessage());
            return List.of();
        }
    }

    private UriComponentsBuilder withApiKey(UriComponentsBuilder builder) {
        if (openalexApiKey != null && !openalexApiKey.isBlank()) {
            builder.queryParam("api_key", openalexApiKey);
        }
        return builder;
    }

    private String extractShortId(String openAlexUrl) {
        if (openAlexUrl == null) return null;
        int lastSlash = openAlexUrl.lastIndexOf('/');
        return lastSlash >= 0 ? openAlexUrl.substring(lastSlash + 1) : openAlexUrl;
    }

    // ── mapping, helpers identical to before ──

    private boolean isRecent(OpenAlexResponseDTO.OpenAlexWorkDTO work, int startYear, LocalDate today) {
        Short publicationYear = work.getPublicationYear();
        if (publicationYear == null || publicationYear < startYear || publicationYear > today.getYear()) return false;
        LocalDate publicationDate = parseDate(work.getPublicationDate());
        return publicationDate == null || !publicationDate.isAfter(today);
    }

    private boolean isRelevant(OpenAlexResponseDTO.OpenAlexWorkDTO work, String abstractText, String query) {
        List<String> tokens = keywordExpansionService.extractTokens(query);
        if (tokens.isEmpty()) return false;
        String normalizedQuery = keywordExpansionService.normalize(query);
        String strongText = buildStrongText(work);
        String fullText = normalize(strongText + " " + nullToEmpty(abstractText));
        if (containsTerm(strongText, normalizedQuery)) return true;
        if (tokens.size() == 1) return containsTokenVariant(strongText, tokens.get(0));
        return tokens.stream().allMatch(token -> containsTokenVariant(fullText, token))
                && tokens.stream().anyMatch(token -> containsTokenVariant(strongText, token));
    }

    private String buildStrongText(OpenAlexResponseDTO.OpenAlexWorkDTO work) {
        StringBuilder text = new StringBuilder();
        append(text, work.getTitle());
        append(text, work.getDisplayName());
        if (work.getTopics() != null) work.getTopics().forEach(topic -> {
            append(text, topic.getDisplayName());
            if (topic.getField() != null) append(text, topic.getField().getDisplayName());
            if (topic.getDomain() != null) append(text, topic.getDomain().getDisplayName());
        });
        if (work.getKeywords() != null) work.getKeywords().forEach(keyword -> append(text, keyword.getDisplayName()));
        return normalize(text.toString());
    }

    private PaperDetailResponseDTO mapToPaper(OpenAlexResponseDTO.OpenAlexWorkDTO work, String abstractText) {
        String doi = normalizeDoi(work.getDoi());
        String sourceUrl = doi != null ? "https://doi.org/" + doi : work.getId();
        UUID paperId = stablePreviewId(work);

        // Register work URL so paper detail can fetch from OpenAlex without DB
        if (work.getId() != null) {
            paperIdToWorkUrl.put(paperId, work.getId());
        }

        PaperDetailResponseDTO dto = PaperDetailResponseDTO.builder()
                .paperId(paperId)
                .title(firstNonBlank(work.getTitle(), work.getDisplayName()))
                .abstractText(abstractText)
                .doi(doi)
                .pubYear(work.getPublicationYear())
                .pubDate(parseDate(work.getPublicationDate()))
                .citationCount(work.getCitedByCount() != null ? work.getCitedByCount() : 0)
                .isOpenAccess(work.getOpenAccess() != null && Boolean.TRUE.equals(work.getOpenAccess().getIsOa()))
                .journalName(work.getPrimaryLocation() != null && work.getPrimaryLocation().getSource() != null
                        ? work.getPrimaryLocation().getSource().getDisplayName() : null)
                .journalId(null)
                .fieldName(resolveFieldName(work))
                .fieldId(null)
                .authors(mapAuthors(work))
                .keywords(mapKeywords(work))
                .sourceUrl(sourceUrl)
                .pdfAvailable(work.getOpenAccess() != null && Boolean.TRUE.equals(work.getOpenAccess().getIsOa()))
                .downloadUrl(sourceUrl)
                .pdfUrl(resolvePdfUrl(work))
                .rating(RatingCalculator.compute(null, work.getCitedByCount(), null))
                .viewCount(0L)
                .bookmarkCount(0L)
                .createdAt(LocalDateTime.now())
                .build();

        // Save to paper cache for persistence across DB switches
        try {
            paperCacheService.save(dto, work.getId());
        } catch (Exception e) {
            log.debug("Failed to cache paper {}: {}", paperId, e.getMessage());
        }

        return dto;
    }

    private UUID stablePreviewId(OpenAlexResponseDTO.OpenAlexWorkDTO work) {
        String key = work.getId() != null ? work.getId() : work.getDoi();
        if (key == null) key = work.getTitle() != null ? work.getTitle() : "";
        return UUID.nameUUIDFromBytes(("openalex:" + key).getBytes(StandardCharsets.UTF_8));
    }

    private List<AuthorDTO> mapAuthors(OpenAlexResponseDTO.OpenAlexWorkDTO work) {
        if (work.getAuthorships() == null) return List.of();
        List<AuthorDTO> authors = new ArrayList<>();
        int order = 1;
        for (OpenAlexResponseDTO.Authorship authorship : work.getAuthorships()) {
            if (order > MAX_AUTHORS) break;
            String name = authorship.getAuthor() != null
                    ? firstNonBlank(authorship.getAuthor().getDisplayName(), authorship.getRawAuthorName())
                    : authorship.getRawAuthorName();
            if (name == null || name.isBlank()) continue;
            authors.add(AuthorDTO.builder().fullName(name)
                    .affiliation(authorship.getRawAffiliationStrings() != null && !authorship.getRawAffiliationStrings().isEmpty()
                            ? authorship.getRawAffiliationStrings().get(0) : null)
                    .totalCitations(0).authorOrder(order).hIndex(0).build());
            order++;
        }
        return authors;
    }

    private List<KeywordDTO> mapKeywords(OpenAlexResponseDTO.OpenAlexWorkDTO work) {
        List<KeywordDTO> keywords = new ArrayList<>();
        if (work.getKeywords() != null) {
            work.getKeywords().stream()
                    .filter(k -> k.getDisplayName() != null && !k.getDisplayName().isBlank())
                    .sorted(Comparator.comparing(k -> k.getScore() != null ? k.getScore() : 0.0d, Comparator.reverseOrder()))
                    .limit(MAX_KEYWORDS)
                    .forEach(k -> keywords.add(KeywordDTO.builder().keywordText(k.getDisplayName()).relevanceScore(k.getScore()).build()));
        }
        if (keywords.isEmpty() && work.getTopics() != null) {
            work.getTopics().stream()
                    .filter(t -> t.getDisplayName() != null && !t.getDisplayName().isBlank())
                    .limit(MAX_KEYWORDS)
                    .forEach(t -> keywords.add(KeywordDTO.builder().keywordText(t.getDisplayName()).relevanceScore(t.getScore()).build()));
        }
        return keywords;
    }

    private String resolveFieldName(OpenAlexResponseDTO.OpenAlexWorkDTO work) {
        if (work.getTopics() == null || work.getTopics().isEmpty()) return null;
        OpenAlexResponseDTO.Topic topic = work.getTopics().get(0);
        return topic.getField() != null ? topic.getField().getDisplayName() : null;
    }

    private String rebuildAbstract(Map<String, List<Integer>> invertedIndex) {
        if (invertedIndex == null || invertedIndex.isEmpty()) return null;
        List<String> words = new ArrayList<>();
        invertedIndex.forEach((word, positions) -> {
            if (positions != null) positions.forEach(pos -> {
                while (words.size() <= pos) words.add("");
                words.set(pos, word);
            });
        });
        String result = words.stream().filter(w -> w != null && !w.isBlank()).collect(Collectors.joining(" "));
        return result.isBlank() ? null : result;
    }

    private boolean containsTokenVariant(String text, String token) {
        return tokenVariants(token).stream().anyMatch(v -> containsTerm(text, v));
    }

    private List<String> tokenVariants(String token) {
        List<String> variants = new ArrayList<>();
        variants.add(token);
        if ("phenomenon".equals(token)) variants.add("phenomena");
        else if ("phenomena".equals(token)) variants.add("phenomenon");
        if (token.endsWith("y") && token.length() > 3) variants.add(token.substring(0, token.length() - 1) + "ies");
        else if (token.endsWith("ies") && token.length() > 4) variants.add(token.substring(0, token.length() - 3) + "y");
        else if (token.endsWith("s") && token.length() > 3) variants.add(token.substring(0, token.length() - 1));
        else if (token.length() > 3) variants.add(token + "s");
        return variants.stream().distinct().collect(Collectors.toList());
    }

    private boolean containsTerm(String text, String term) {
        String nt = normalize(text), nterm = normalize(term);
        if (nt.isBlank() || nterm.isBlank()) return false;
        return (" " + nt + " ").contains(" " + nterm + " ");
    }

    private String normalize(String v) { return keywordExpansionService.normalize(v); }

    private String normalizeOpenAlexSearchQuery(String query) {
        if (query == null) return "";
        return query.replace("&", " ").replace("/", " ").replace("\\", " ").trim().replaceAll("\\s+", " ");
    }

    /**
     * Wraps a filter value in double quotes so OpenAlex treats it as a single value.
     * Required for multi-word queries in comma-separated filter parameters.
     * Escapes any embedded double quotes.
     */
    private String quotedFilterValue(String value) {
        if (value == null || value.isBlank()) return "\"\"";
        return "\"" + value.replace("\"", "\\\"") + "\"";
    }

    private String normalizeDoi(String doi) {
        if (doi == null || doi.isBlank()) return null;
        return doi.replace("https://doi.org/", "").replace("http://doi.org/", "").trim();
    }

    private LocalDate parseDate(String date) {
        if (date == null || date.isBlank()) return null;
        try { return LocalDate.parse(date); } catch (Exception e) { return null; }
    }

    private String firstNonBlank(String a, String b) { return a != null && !a.isBlank() ? a : b; }
    private String nullToEmpty(String v) { return v == null ? "" : v; }
    private void append(StringBuilder sb, String v) { if (v != null && !v.isBlank()) sb.append(' ').append(v); }

    private String resolvePdfUrl(OpenAlexResponseDTO.OpenAlexWorkDTO work) {
        if (work.getBestOaLocation() != null && work.getBestOaLocation().getPdfUrl() != null
                && !work.getBestOaLocation().getPdfUrl().isBlank()) return work.getBestOaLocation().getPdfUrl();
        if (work.getPrimaryLocation() != null && work.getPrimaryLocation().getPdfUrl() != null
                && !work.getPrimaryLocation().getPdfUrl().isBlank()) return work.getPrimaryLocation().getPdfUrl();
        if (work.getOpenAccess() != null && work.getOpenAccess().getOaUrl() != null
                && !work.getOpenAccess().getOaUrl().isBlank()) return work.getOpenAccess().getOaUrl();
        return null;
    }

    private record WorkWithAbstract(OpenAlexResponseDTO.OpenAlexWorkDTO work, String abstractText) {}

    // ── DTO for OpenAlex group_by response ──

    @lombok.Data
    private static class OpenAlexGroupByResponse {
        @com.fasterxml.jackson.annotation.JsonProperty("group_by")
        private List<GroupByEntry> groupBy;
    }

    @lombok.Data
    private static class GroupByEntry {
        private String key;
        @com.fasterxml.jackson.annotation.JsonProperty("key_display_name")
        private String keyDisplayName;
        private Integer count;
    }
}
