package com.sra.journal_tracking.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sra.journal_tracking.dto.author.AuthorQuickStatsResponse;
import com.sra.journal_tracking.dto.author.AuthorResearchFocusResponse;
import com.sra.journal_tracking.dto.author.AuthorTimelineResponse;
import com.sra.journal_tracking.dto.author.CoAuthorResponse;
import com.sra.journal_tracking.dto.author.OpenAlexAuthorResponseDTO;
import com.sra.journal_tracking.dto.author.OpenAlexWorksResponseDTO;
import com.sra.journal_tracking.dto.paper.PaperDetailResponseDTO;
import com.sra.journal_tracking.dto.sync.OpenAlexResponseDTO;
import com.sra.journal_tracking.entity.jpa.ApiSource;
import com.sra.journal_tracking.entity.jpa.Author;
import com.sra.journal_tracking.exception.AppException;
import com.sra.journal_tracking.exception.ErrorCode;
import com.sra.journal_tracking.repository.jpa.ApiSourceRepository;
import com.sra.journal_tracking.repository.jpa.AuthorRepository;
import com.sra.journal_tracking.repository.jpa.ResearchPaperRepository;
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
import java.util.UUID;
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
    private final AuthorRepository authorRepository;
    private final ApiSourceRepository apiSourceRepository;
    private final ResearchPaperRepository researchPaperRepository;
    private final DataSyncService dataSyncService;

    @Value("${app.openalex-email:}")
    private String openalexEmail;

    @Value("${app.openalex-api-key:}")
    private String openalexApiKey;

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
            // Use sum of all topic counts as denominator so percentages are meaningful
            int totalTopicCount = bestMatch.getTopics().stream()
                    .mapToInt(t -> t.getCount() != null ? t.getCount() : 0)
                    .sum();
            final int denominator = totalTopicCount > 0 ? totalTopicCount : 1;

            topicList = bestMatch.getTopics().stream()
                    .filter(t -> t.getDisplayName() != null && (t.getCount() != null && t.getCount() > 0))
                    .map(t -> {
                        int count = t.getCount();
                        double pct = (count * 100.0) / denominator;

                        return AuthorResearchFocusResponse.TopicFocus.builder()
                                .topicName(t.getDisplayName())
                                .paperCount(count)
                                .percentage(Math.round(pct * 10.0) / 10.0) // 1 decimal place
                                .subfield(t.getSubfield() != null ? t.getSubfield().getDisplayName() : null)
                                .field(t.getField() != null ? t.getField().getDisplayName() : null)
                                .domain(t.getDomain() != null ? t.getDomain().getDisplayName() : null)
                                .build();
                    })
                    .filter(t -> t.getPercentage() > 0) // skip 0% topics
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
     * Get an author's top cited papers (all time, no year filter).
     * DB-first: checks local DB; falls back to OpenAlex API if not found.
     *
     * @param keyword author name to search for
     * @return list of top 5 PaperDetailResponseDTO sorted by citation count desc
     */
    @Cacheable(value = "search:authorTopPapers", cacheManager = "searchCacheManager",
               key = "#keyword.trim().toLowerCase()", unless = "#result == null || #result.isEmpty()")
    public List<PaperDetailResponseDTO> getTopPapers(String keyword) {
        String trimmedKeyword = keyword.trim();
        if (trimmedKeyword.isEmpty()) {
            throw new IllegalArgumentException("Author name cannot be empty");
        }

        // ── 1. Try local DB first ──
        List<PaperDetailResponseDTO> fromDb = fetchTopPapersFromDb(trimmedKeyword);
        if (!fromDb.isEmpty()) {
            log.info("TopPapers: found {} papers in local DB for '{}'", fromDb.size(), trimmedKeyword);
            return fromDb;
        }

        // ── 2. Fallback: OpenAlex API ──
        return fetchTopPapersFromOpenAlex(trimmedKeyword);
    }

    /** Try to find top papers in local DB by author name. */
    private List<PaperDetailResponseDTO> fetchTopPapersFromDb(String authorName) {
        try {
            var author = authorRepository.findFirstByFullName(authorName).orElse(null);
            if (author == null) return List.of();

            var papers = researchPaperRepository.findTopCitedByAuthorName(
                    author.getFullName(), org.springframework.data.domain.PageRequest.of(0, 5));
            if (papers == null || papers.isEmpty()) return List.of();

            return papers.stream().map(this::mapEntityToPaper).toList();
        } catch (Exception e) {
            log.warn("TopPapers DB lookup failed for '{}': {}", authorName, e.getMessage());
            return List.of();
        }
    }

    /** Map a JPA ResearchPaper entity to PaperDetailResponseDTO. */
    private PaperDetailResponseDTO mapEntityToPaper(com.sra.journal_tracking.entity.jpa.ResearchPaper p) {
        return PaperDetailResponseDTO.builder()
                .paperId(p.getPaperId())
                .title(p.getTitle())
                .abstractText(p.getAbstractText())
                .doi(p.getDoi())
                .pubYear(p.getPubYear())
                .pubDate(p.getPubDate())
                .citationCount(p.getCitationCount())
                .isOpenAccess(p.getIsOpenAccess())
                .journalName(p.getJournal() != null ? p.getJournal().getJournalName() : null)
                .journalId(p.getJournal() != null ? p.getJournal().getJournalId() : null)
                .sourceUrl(p.getDoi() != null ? "https://doi.org/" + p.getDoi() : null)
                .pdfAvailable(p.getPdfUrl() != null)
                .pdfUrl(p.getPdfUrl())
                .createdAt(p.getCreatedAt())
                .build();
    }

    /** Fetch top cited papers from OpenAlex API by author ID. */
    private List<PaperDetailResponseDTO> fetchTopPapersFromOpenAlex(String keyword) {
        // ── Find author on OpenAlex ──
        String url = buildUrl(keyword);
        log.info("TopPapers: finding author '{}' on OpenAlex", keyword);

        String rawJson = fetchRawWithRetry(url, keyword);
        if (rawJson == null) throw new AppException(ErrorCode.EXTERNAL_API_ERROR);

        OpenAlexAuthorResponseDTO response;
        try {
            response = objectMapper.readValue(rawJson, OpenAlexAuthorResponseDTO.class);
        } catch (Exception e) {
            log.error("TopPapers: failed to parse author response: {}", e.getMessage());
            throw new AppException(ErrorCode.EXTERNAL_API_ERROR);
        }

        if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
            throw new AppException(ErrorCode.AUTHOR_NOT_FOUND);
        }

        var bestMatch = pickBestMatch(response.getResults(), keyword);
        if (bestMatch == null) throw new AppException(ErrorCode.AUTHOR_NOT_FOUND);

        String shortId = extractShortId(bestMatch.getId());
        log.info("TopPapers: best match = {} (id={})", bestMatch.getDisplayName(), shortId);

        // ── Fetch works ──
        String worksUrl = UriComponentsBuilder
                .fromHttpUrl("https://api.openalex.org/works")
                .queryParam("filter", "authorships.author.id:" + shortId)
                .queryParam("per-page", 5)
                .queryParam("sort", "cited_by_count:desc")
                .build().encode().toUriString();

        log.info("TopPapers: fetching works from {}", worksUrl);

        String worksRawJson = fetchRawWithRetry(worksUrl, keyword);
        if (worksRawJson == null) throw new AppException(ErrorCode.EXTERNAL_API_ERROR);

        OpenAlexResponseDTO worksResponse;
        try {
            worksResponse = objectMapper.readValue(worksRawJson, OpenAlexResponseDTO.class);
        } catch (Exception e) {
            log.error("TopPapers: failed to parse works response: {}", e.getMessage());
            throw new AppException(ErrorCode.EXTERNAL_API_ERROR);
        }

        if (worksResponse == null || worksResponse.getResults() == null || worksResponse.getResults().isEmpty()) {
            log.warn("TopPapers: no works found for {}", bestMatch.getDisplayName());
            return List.of();
        }

        List<OpenAlexResponseDTO.OpenAlexWorkDTO> rawWorks = worksResponse.getResults().stream()
                .limit(5)
                .collect(Collectors.toList());

        // Fire-and-forget: async save to local DB (save-on-search)
        try {
            dataSyncService.saveWorksFromOpenAlexAsync(rawWorks);
        } catch (Exception e) {
            log.debug("Save-on-search dispatch failed for author '{}': {}", keyword, e.getMessage());
        }

        return rawWorks.stream()
                .map(this::mapWorkToPaper)
                .toList();
    }

    /** Map an OpenAlex work to PaperDetailResponseDTO. */
    private PaperDetailResponseDTO mapWorkToPaper(OpenAlexResponseDTO.OpenAlexWorkDTO work) {
        String doi = work.getDoi() != null
                ? work.getDoi().replace("https://doi.org/", "").trim() : null;
        var source = work.getPrimaryLocation() != null ? work.getPrimaryLocation().getSource() : null;

        return PaperDetailResponseDTO.builder()
                .paperId(java.util.UUID.nameUUIDFromBytes(
                        ("openalex-author:" + work.getId()).getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .title(work.getTitle() != null ? work.getTitle() : work.getDisplayName())
                .abstractText(rebuildAbstract(work.getAbstractInvertedIndex()))
                .doi(doi)
                .pubYear(work.getPublicationYear())
                .pubDate(work.getPublicationDate() != null
                        ? java.time.LocalDate.parse(work.getPublicationDate()) : null)
                .citationCount(work.getCitedByCount() != null ? work.getCitedByCount() : 0)
                .isOpenAccess(work.getOpenAccess() != null
                        && Boolean.TRUE.equals(work.getOpenAccess().getIsOa()))
                .journalName(source != null ? source.getDisplayName() : null)
                .sourceUrl(doi != null ? "https://doi.org/" + doi : work.getId())
                .pdfAvailable(work.getBestOaLocation() != null
                        && work.getBestOaLocation().getPdfUrl() != null)
                .pdfUrl(work.getBestOaLocation() != null ? work.getBestOaLocation().getPdfUrl() : null)
                .createdAt(java.time.LocalDateTime.now())
                .build();
    }

    /** Rebuild abstract text from OpenAlex inverted index. */
    private String rebuildAbstract(Map<String, ? extends List<Integer>> invertedIndex) {
        if (invertedIndex == null || invertedIndex.isEmpty()) return null;
        var entries = new ArrayList<java.util.AbstractMap.SimpleEntry<Integer, String>>();
        for (var e : invertedIndex.entrySet()) {
            var positions = e.getValue();
            if (positions != null) {
                for (int pos : positions) entries.add(new java.util.AbstractMap.SimpleEntry<>(pos, e.getKey()));
            }
        }
        entries.sort(Map.Entry.comparingByKey());
        return entries.stream().map(java.util.AbstractMap.SimpleEntry::getValue)
                .collect(Collectors.joining(" "));
    }

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
        // Use the simple 'search' query param — OpenAlex translates internally
        // to text.search filter (as of 2026). The old display_name.search filter
        // is no longer accepted by the API edge.

        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(OPEN_ALEX_AUTHORS_URL)
                .queryParam("search", keyword)
                .queryParam("per-page", MAX_RESULTS);

        // api_key required since Feb 2026 (replaces deprecated mailto pool)
        if (openalexApiKey != null && !openalexApiKey.isBlank()) {
            builder.queryParam("api_key", openalexApiKey);
        } else if (openalexEmail != null && !openalexEmail.isBlank()) {
            builder.queryParam("mailto", openalexEmail); // fallback (deprecated)
        }

        return builder.build().toUriString();
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

        // ── Upsert author into local DB for follow/bookmark support ──
        UUID localAuthorId = upsertLocalAuthor(result);

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
                .authorId(localAuthorId)
                .build();
    }

    /**
     * Look up or create a local Author entity from OpenAlex data.
     * This enables follow/bookmark features that require a local DB UUID.
     *
     * @param result the OpenAlex author result
     * @return the local Author UUID, or null if the upsert failed
     */
    private UUID upsertLocalAuthor(OpenAlexAuthorResponseDTO.AuthorResult result) {
        try {
            String openAlexId = result.getId();
            if (openAlexId == null || openAlexId.isBlank()) return null;

            String shortId = extractShortId(openAlexId);
            if (shortId == null) return null;

            // Find the OpenAlex ApiSource
            ApiSource openAlexSource = apiSourceRepository.findBySourceNameIgnoreCase("OpenAlex")
                    .orElse(null);
            if (openAlexSource == null) {
                log.warn("OpenAlex ApiSource not found in local DB — cannot upsert author");
                return null;
            }

            // Look up existing author by external ID + source
            Author author = authorRepository
                    .findByExternalAuthorIdAndSource_SourceId(shortId, openAlexSource.getSourceId())
                    .orElse(null);

            if (author == null) {
                // Create a new minimal Author entity
                author = Author.builder()
                        .source(openAlexSource)
                        .externalAuthorId(shortId)
                        .fullName(result.getDisplayName() != null ? result.getDisplayName() : "Unknown")
                        .affiliation(result.getLastKnownInstitution() != null
                                ? result.getLastKnownInstitution().getDisplayName() : null)
                        .hIndex(result.getSummaryStats() != null && result.getSummaryStats().getHIndex() != null
                                ? result.getSummaryStats().getHIndex()
                                : (result.getHIndex() != null ? result.getHIndex() : 0))
                        .totalCitations(result.getCitedByCount() != null ? result.getCitedByCount() : 0)
                        .i10Index(result.getSummaryStats() != null && result.getSummaryStats().getI10Index() != null
                                ? result.getSummaryStats().getI10Index()
                                : (result.getI10Index() != null ? result.getI10Index() : 0))
                        .worksCount(result.getWorksCount() != null ? result.getWorksCount() : 0)
                        .build();
                author = authorRepository.save(author);
                log.info("Created local Author: id={}, name={}", author.getAuthorId(), author.getFullName());
            } else {
                // Update existing author with latest metrics from OpenAlex
                boolean updated = false;
                if (result.getDisplayName() != null && !result.getDisplayName().equals(author.getFullName())) {
                    author.setFullName(result.getDisplayName());
                    updated = true;
                }
                Integer hIdx = result.getSummaryStats() != null && result.getSummaryStats().getHIndex() != null
                        ? result.getSummaryStats().getHIndex() : result.getHIndex();
                if (hIdx != null && !hIdx.equals(author.getHIndex())) {
                    author.setHIndex(hIdx);
                    updated = true;
                }
                Integer i10 = result.getSummaryStats() != null && result.getSummaryStats().getI10Index() != null
                        ? result.getSummaryStats().getI10Index() : result.getI10Index();
                if (i10 != null && !i10.equals(author.getI10Index())) {
                    author.setI10Index(i10);
                    updated = true;
                }
                if (result.getCitedByCount() != null && !result.getCitedByCount().equals(author.getTotalCitations())) {
                    author.setTotalCitations(result.getCitedByCount());
                    updated = true;
                }
                if (result.getWorksCount() != null && !result.getWorksCount().equals(author.getWorksCount())) {
                    author.setWorksCount(result.getWorksCount());
                    updated = true;
                }
                if (updated) {
                    author = authorRepository.save(author);
                    log.info("Updated local Author metrics: id={}, hIndex={}", author.getAuthorId(), author.getHIndex());
                }
            }

            return author.getAuthorId();
        } catch (Exception e) {
            log.warn("Failed to upsert local Author for OpenAlex ID {}: {}",
                    result.getId(), e.getMessage());
            return null;
        }
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
