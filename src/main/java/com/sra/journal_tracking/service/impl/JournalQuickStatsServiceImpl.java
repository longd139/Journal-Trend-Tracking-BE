package com.sra.journal_tracking.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sra.journal_tracking.dto.journal.JournalAuthorResponse;
import com.sra.journal_tracking.dto.journal.JournalQuickStatsResponse;
import com.sra.journal_tracking.dto.journal.JournalSuggestionResponse;
import com.sra.journal_tracking.dto.journal.JournalTimelineResponse;
import com.sra.journal_tracking.dto.journal.JournalTimelineResponse.YearlyDataPoint;
import com.sra.journal_tracking.dto.paper.AuthorDTO;
import com.sra.journal_tracking.dto.paper.PaperDetailResponseDTO;
import com.sra.journal_tracking.dto.sync.OpenAlexResponseDTO;
import com.sra.journal_tracking.entity.jpa.ResearchPaper;
import com.sra.journal_tracking.repository.jpa.PaperAuthorRepository;
import com.sra.journal_tracking.repository.jpa.ResearchPaperRepository;
import com.sra.journal_tracking.service.JournalQuickStatsService;
import com.sra.journal_tracking.service.OpenAlexFallbackSearchService;
import com.sra.journal_tracking.service.PaperCacheService;
import com.sra.journal_tracking.service.DataSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class JournalQuickStatsServiceImpl implements JournalQuickStatsService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final OpenAlexFallbackSearchService openAlexSearchService;
    private final PaperCacheService paperCacheService;
    private final DataSyncService dataSyncService;
    private final ResearchPaperRepository researchPaperRepository;
    private final PaperAuthorRepository paperAuthorRepository;

    @Value("${app.openalex-api-key:}")
    private String openalexApiKey;

    private static final int MAX_RETRIES = 3;
    private static final int STALE_DAYS = 7;

    public JournalQuickStatsServiceImpl(RestTemplate restTemplate,
                                         ObjectMapper objectMapper,
                                         OpenAlexFallbackSearchService openAlexSearchService,
                                         PaperCacheService paperCacheService,
                                         DataSyncService dataSyncService,
                                         ResearchPaperRepository researchPaperRepository,
                                         PaperAuthorRepository paperAuthorRepository) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.openAlexSearchService = openAlexSearchService;
        this.paperCacheService = paperCacheService;
        this.dataSyncService = dataSyncService;
        this.researchPaperRepository = researchPaperRepository;
        this.paperAuthorRepository = paperAuthorRepository;
    }

    // ═══════════════════════════════════════════════════════
    //  getStats — journal prestige KPIs
    // ═══════════════════════════════════════════════════════

    @Override
    @Cacheable(value = "search:journalQuickStats", cacheManager = "searchCacheManager",
               key = "#journalName.trim().toLowerCase()", unless = "#result == null || #result.totalPapers == 0")
    public JournalQuickStatsResponse getStats(String journalName) {
        String trimmed = journalName.trim();
        if (trimmed.isEmpty()) return buildEmptyResponse(journalName);

        // ── 1. Try local DB first (with 7-day staleness check) ──
        JournalQuickStatsResponse fromDb = fetchJournalStatsFromDb(trimmed);
        if (fromDb != null) {
            log.info("JournalQuickStats: found in local DB for '{}'", trimmed);
            return fromDb;
        }

        // ── 2. Fallback: OpenAlex API ──
        return fetchJournalStatsFromOpenAlex(trimmed);
    }

    private JournalQuickStatsResponse fetchJournalStatsFromDb(String journalName) {
        try {
            // 7-day staleness check
            var latest = researchPaperRepository.findLatestPaperDateByJournalName(journalName);
            if (latest.isEmpty()
                    || latest.get().isBefore(LocalDateTime.now().minus(STALE_DAYS, ChronoUnit.DAYS))) {
                return null;
            }

            long totalPapers = researchPaperRepository.countByJournal_JournalName(journalName);
            long totalCitations = researchPaperRepository.sumCitationsByJournalName(journalName);
            if (totalPapers == 0) return null;

            Double citeScore = totalPapers > 0
                    ? Math.round((double) totalCitations / totalPapers * 100.0) / 100.0 : null;

            return JournalQuickStatsResponse.builder()
                    .journalName(journalName)
                    .totalPapers(totalPapers)
                    .totalCitations(totalCitations)
                    .calculatedCiteScore(citeScore)
                    .avgCitationsPerPaper(totalPapers > 0
                            ? Math.round((double) totalCitations / totalPapers * 10.0) / 10.0 : null)
                    .dataSource("database")
                    .build();
        } catch (Exception e) {
            log.warn("JournalQuickStats DB lookup failed for '{}': {}", journalName, e.getMessage());
            return null;
        }
    }

    private JournalQuickStatsResponse fetchJournalStatsFromOpenAlex(String journalName) {
        log.info("Journal quick stats via OpenAlex: '{}'", journalName);

        var journal = resolveJournal(journalName);
        if (journal == null) return buildEmptyResponse(journalName);

        long worksCount = jsonLong(journal, "works_count");
        long citedByCount = jsonLong(journal, "cited_by_count");
        Double citeScore = worksCount > 0 ? Math.round((double) citedByCount / worksCount * 100.0) / 100.0 : null;
        Double avgCitations = worksCount > 0
                ? Math.round((double) citedByCount / worksCount * 10.0) / 10.0 : null;

        List<String> topKeywords = getTopKeywordsFromOpenAlex(jsonStr(journal, "id"));

        return JournalQuickStatsResponse.builder()
                .journalId(jsonStr(journal, "id"))
                .journalName(jsonStr(journal, "display_name"))
                .issn(jsonStr(journal, "issn_l"))
                .publisher(jsonStr(journal, "host_organization_name"))
                .impactFactor(null)
                .calculatedCiteScore(citeScore)
                .quartile(null)
                .totalPapers(worksCount)
                .totalCitations(citedByCount)
                .avgCitationsPerPaper(avgCitations)
                .topKeywords(topKeywords)
                .dataSource("openalex")
                .build();
    }

    // ═══════════════════════════════════════════════════════
    //  getTimeline — year-by-year paper & citation counts
    // ═══════════════════════════════════════════════════════

    @Override
    @Cacheable(value = "search:journalTimeline", cacheManager = "searchCacheManager",
               key = "#journalName.trim().toLowerCase()",
               unless = "#result == null || #result.timeline == null || #result.timeline.isEmpty()")
    public JournalTimelineResponse getTimeline(String journalName) {
        String trimmed = journalName.trim();
        if (trimmed.isEmpty()) return JournalTimelineResponse.builder()
                .journalName(journalName).timeline(List.of()).build();
        log.info("Journal timeline via OpenAlex: '{}'", trimmed);

        var journal = resolveJournal(trimmed);
        if (journal == null) return JournalTimelineResponse.builder()
                .journalName(trimmed).timeline(List.of()).build();

        // Fetch detailed source info for counts_by_year
        String sourceId = extractShortId(jsonStr(journal, "id"));
        String url = buildUrl("/sources/" + sourceId);
        var detail = fetchJson(url, trimmed);
        if (detail == null) return JournalTimelineResponse.builder()
                .journalName(jsonStr(journal, "display_name")).timeline(List.of()).build();

        // Parse counts_by_year
        List<YearlyDataPoint> timeline = new ArrayList<>();
        long totalPapers = 0, totalCitations = 0;
        var countsByYear = (List<Map<String, Object>>) detail.get("counts_by_year");
        if (countsByYear != null) {
            for (var y : countsByYear) {
                long papers = jsonLong(y, "works_count");
                long citations = jsonLong(y, "cited_by_count");
                timeline.add(YearlyDataPoint.builder()
                        .year(((Number) y.get("year")).intValue())
                        .paperCount(papers)
                        .citationCount(citations)
                        .avgCitationsPerPaper(papers > 0
                                ? Math.round((double) citations / papers * 10.0) / 10.0 : null)
                        .build());
                totalPapers += papers;
                totalCitations += citations;
            }
        }

        return JournalTimelineResponse.builder()
                .journalId(jsonStr(journal, "id"))
                .journalName(jsonStr(journal, "display_name"))
                .issn(jsonStr(journal, "issn_l"))
                .publisher(jsonStr(journal, "host_organization_name"))
                .impactFactor(null).quartile(null)
                .totalPapers(totalPapers).totalCitations(totalCitations)
                .dataSource("openalex")
                .timeline(timeline).build();
    }

    // ═══════════════════════════════════════════════════════
    //  getTopPapers — top 5 most-cited papers in journal
    // ═══════════════════════════════════════════════════════

    @Override
    @Cacheable(value = "search:journalTopPapers", cacheManager = "searchCacheManager",
               key = "#journalName.trim().toLowerCase()", unless = "#result == null || #result.isEmpty()")
    public List<PaperDetailResponseDTO> getTopPapers(String journalName) {
        String trimmed = journalName.trim();
        if (trimmed.isEmpty()) return List.of();

        // ── 1. Try local DB first (with 7-day staleness check) ──
        List<PaperDetailResponseDTO> fromDb = fetchJournalTopPapersFromDb(trimmed);
        if (!fromDb.isEmpty()) {
            log.info("JournalTopPapers: found {} papers in local DB for '{}'", fromDb.size(), trimmed);
            return fromDb;
        }

        // ── 2. Fallback: OpenAlex API ──
        return fetchJournalTopPapersFromOpenAlex(trimmed);
    }

    private List<PaperDetailResponseDTO> fetchJournalTopPapersFromDb(String journalName) {
        try {
            var latest = researchPaperRepository.findLatestPaperDateByJournalName(journalName);
            if (latest.isEmpty()
                    || latest.get().isBefore(LocalDateTime.now().minus(STALE_DAYS, ChronoUnit.DAYS))) {
                return List.of();
            }

            var papers = researchPaperRepository.findTopCitedByJournalName(
                    journalName, PageRequest.of(0, 5));
            if (papers == null || papers.isEmpty()) return List.of();

            return papers.stream().map(this::mapEntityToPaper).toList();
        } catch (Exception e) {
            log.warn("JournalTopPapers DB lookup failed: {}", e.getMessage());
            return List.of();
        }
    }

    private PaperDetailResponseDTO mapEntityToPaper(ResearchPaper p) {
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

    private List<PaperDetailResponseDTO> fetchJournalTopPapersFromOpenAlex(String journalName) {
        log.info("Journal top papers via OpenAlex: '{}'", journalName);

        var journal = resolveJournal(journalName);
        if (journal == null) return List.of();

        String sourceId = extractShortId(jsonStr(journal, "id"));
        String url = UriComponentsBuilder.fromHttpUrl("https://api.openalex.org/works")
                .queryParam("filter", "primary_location.source.id:" + sourceId)
                .queryParam("sort", "cited_by_count:desc")
                .queryParam("per-page", 5)
                .queryParam("select", "id,doi,title,display_name,publication_year,publication_date,"
                        + "cited_by_count,abstract_inverted_index,open_access,"
                        + "primary_location,best_oa_location,topics,keywords,authorships")
                .queryParam("api_key", openalexApiKey)
                .build().toUriString();

        OpenAlexResponseDTO response = fetchOpenAlex(url, journalName);
        if (response == null || response.getResults() == null) return List.of();

        List<OpenAlexResponseDTO.OpenAlexWorkDTO> rawWorks = new ArrayList<>(response.getResults());

        // Fire-and-forget: async save to local DB (save-on-search)
        if (!rawWorks.isEmpty()) {
            try {
                dataSyncService.saveWorksFromOpenAlexAsync(rawWorks);
            } catch (Exception e) {
                log.debug("Save-on-search dispatch failed for journal '{}': {}", journalName, e.getMessage());
            }
        }

        return rawWorks.stream()
                .map(this::mapWorkToPaper)
                .collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════
    //  getTopAuthors — top contributing authors in journal
    // ═══════════════════════════════════════════════════════

    @Override
    @Cacheable(value = "search:journalTopAuthors", cacheManager = "searchCacheManager",
               key = "#journalName.trim().toLowerCase()", unless = "#result == null || #result.isEmpty()")
    public List<JournalAuthorResponse> getTopAuthors(String journalName) {
        String trimmed = journalName.trim();
        if (trimmed.isEmpty()) return List.of();

        // ── 1. Try local DB first (with 7-day staleness check) ──
        List<JournalAuthorResponse> fromDb = fetchJournalTopAuthorsFromDb(trimmed);
        if (!fromDb.isEmpty()) {
            log.info("JournalTopAuthors: found {} authors in local DB for '{}'", fromDb.size(), trimmed);
            return fromDb;
        }

        // ── 2. Fallback: OpenAlex API ──
        return fetchJournalTopAuthorsFromOpenAlex(trimmed);
    }

    private List<JournalAuthorResponse> fetchJournalTopAuthorsFromDb(String journalName) {
        try {
            var latest = researchPaperRepository.findLatestPaperDateByJournalName(journalName);
            if (latest.isEmpty()
                    || latest.get().isBefore(LocalDateTime.now().minus(STALE_DAYS, ChronoUnit.DAYS))) {
                return List.of();
            }

            var rows = paperAuthorRepository.findTopAuthorsByJournalName(
                    journalName, PageRequest.of(0, 10));
            if (rows == null || rows.isEmpty()) return List.of();

            return rows.stream()
                    .map(row -> {
                        long paperCount = ((Number) row[3]).longValue();
                        long totalCitations = ((Number) row[4]).longValue();
                        return JournalAuthorResponse.builder()
                                .authorName((String) row[0])
                                .openAlexId(row[2] != null
                                        ? "https://openalex.org/" + row[2] : null)
                                .paperCount(paperCount)
                                .totalCitations(totalCitations)
                                .avgCitationsPerPaper(paperCount > 0
                                        ? Math.round((double) totalCitations / paperCount * 10.0) / 10.0
                                        : null)
                                .dataSource("database")
                                .build();
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("JournalTopAuthors DB lookup failed: {}", e.getMessage());
            return List.of();
        }
    }

    private List<JournalAuthorResponse> fetchJournalTopAuthorsFromOpenAlex(String journalName) {
        log.info("Journal top authors via OpenAlex: '{}'", journalName);

        var journal = resolveJournal(journalName);
        if (journal == null) return List.of();

        String sourceId = extractShortId(jsonStr(journal, "id"));
        String url = UriComponentsBuilder.fromHttpUrl("https://api.openalex.org/works")
                .queryParam("filter", "primary_location.source.id:" + sourceId)
                .queryParam("sort", "cited_by_count:desc")
                .queryParam("per-page", 50)
                .queryParam("api_key", openalexApiKey)
                .build().toUriString();

        OpenAlexResponseDTO response = fetchOpenAlex(url, journalName);
        if (response == null || response.getResults() == null) return List.of();

        // Aggregate authors across top works
        Map<String, JournalAuthorAggregate> authorMap = new LinkedHashMap<>();
        for (var work : response.getResults()) {
            if (work.getAuthorships() == null) continue;
            int cit = work.getCitedByCount() != null ? work.getCitedByCount() : 0;
            for (var auth : work.getAuthorships()) {
                String name = null;
                String oaId = null;
                if (auth.getAuthor() != null) {
                    name = auth.getAuthor().getDisplayName();
                    oaId = auth.getAuthor().getId();
                }
                if (name == null || name.isBlank()) name = auth.getRawAuthorName();
                if (name == null || name.isBlank()) continue;

                var agg = authorMap.computeIfAbsent(name, k -> new JournalAuthorAggregate());
                agg.name = name;
                if (agg.openAlexId == null) agg.openAlexId = oaId;
                agg.paperCount++;
                agg.totalCitations += cit;
            }
        }

        // Fire-and-forget: async save to local DB
        if (!response.getResults().isEmpty()) {
            try {
                dataSyncService.saveWorksFromOpenAlexAsync(new ArrayList<>(response.getResults()));
            } catch (Exception e) {
                log.debug("Save-on-search dispatch failed for journal '{}': {}", journalName, e.getMessage());
            }
        }

        return authorMap.values().stream()
                .sorted(Comparator.comparingLong(JournalAuthorAggregate::getPaperCount).reversed())
                .limit(10)
                .map(a -> JournalAuthorResponse.builder()
                        .authorName(a.name)
                        .openAlexId(a.openAlexId)
                        .paperCount(a.paperCount)
                        .totalCitations(a.totalCitations)
                        .avgCitationsPerPaper(a.paperCount > 0
                                ? Math.round((double) a.totalCitations / a.paperCount * 10.0) / 10.0 : null)
                        .dataSource("openalex")
                        .build())
                .collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════════════

    /** Resolve journal name → OpenAlex /sources JSON map. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveJournal(String name) {
        String url = UriComponentsBuilder.fromHttpUrl("https://api.openalex.org/sources")
                .queryParam("search", name)
                .queryParam("per-page", 1)
                .queryParam("api_key", openalexApiKey)
                .build().toUriString();

        Map<String, Object> raw = fetchJson(url, name);
        if (raw == null) return null;
        List<Map<String, Object>> results = (List<Map<String, Object>>) raw.get("results");
        if (results == null || results.isEmpty()) return null;
        return results.get(0);
    }

    private List<String> getTopKeywordsFromOpenAlex(String sourceUrl) {
        try {
            String sid = extractShortId(sourceUrl);
            String url = UriComponentsBuilder.fromHttpUrl("https://api.openalex.org/works")
                    .queryParam("filter", "primary_location.source.id:" + sid)
                    .queryParam("group_by", "keywords.id")
                    .queryParam("per-page", 5)
                    .queryParam("api_key", openalexApiKey)
                    .build().toUriString();
            var raw = fetchJson(url, sid);
            if (raw == null) return List.of();
            List<Map<String, Object>> groups = (List<Map<String, Object>>) raw.get("group_by");
            if (groups == null) return List.of();
            return groups.stream()
                    .map(g -> (String) g.get("key_display_name"))
                    .filter(Objects::nonNull)
                    .limit(5)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Failed to get top keywords from OpenAlex: {}", e.getMessage());
            return List.of();
        }
    }

    private String buildUrl(String path) {
        return UriComponentsBuilder.fromHttpUrl("https://api.openalex.org" + path)
                .queryParam("api_key", openalexApiKey)
                .build().toUriString();
    }

    private Map<String, Object> fetchJson(String url, String context) {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                String raw = restTemplate.getForObject(url, String.class);
                if (raw == null || raw.isBlank()) return null;
                return objectMapper.readValue(raw, Map.class);
            } catch (Exception e) {
                if (attempt < MAX_RETRIES - 1) {
                    try { Thread.sleep((attempt + 1) * 2000L); }
                    catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                } else {
                    log.warn("OpenAlex fetch failed for '{}': {}", context, e.getMessage());
                }
            }
        }
        return null;
    }

    private OpenAlexResponseDTO fetchOpenAlex(String url, String context) {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                String raw = restTemplate.getForObject(url, String.class);
                if (raw == null || raw.isBlank()) return null;
                return objectMapper.readValue(raw, OpenAlexResponseDTO.class);
            } catch (Exception e) {
                if (attempt < MAX_RETRIES - 1) {
                    try { Thread.sleep((attempt + 1) * 2000L); }
                    catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                } else {
                    log.warn("OpenAlex fetch failed for '{}': {}", context, e.getMessage());
                }
            }
        }
        return null;
    }

    private static String extractShortId(String sourceUrl) {
        if (sourceUrl == null) return "";
        int lastSlash = sourceUrl.lastIndexOf('/');
        return lastSlash >= 0 ? sourceUrl.substring(lastSlash + 1) : sourceUrl;
    }

    private static String jsonStr(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }

    private static long jsonLong(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v instanceof Number n) return n.longValue();
        return 0L;
    }

    private JournalQuickStatsResponse buildEmptyResponse(String query) {
        return JournalQuickStatsResponse.builder()
                .journalName(query).totalPapers(0L).totalCitations(0L).build();
    }

    /** Mutable aggregate for author counting. */
    private static class JournalAuthorAggregate {
        String name, openAlexId;
        long paperCount, totalCitations;
        long getPaperCount() { return paperCount; }
    }

    // ── Work → Paper mapping (reused from OpenAlexFallbackSearchService pattern) ──

    private PaperDetailResponseDTO mapWorkToPaper(OpenAlexResponseDTO.OpenAlexWorkDTO work) {
        String doi = work.getDoi() != null
                ? work.getDoi().replace("https://doi.org/", "").trim() : null;
        var source = work.getPrimaryLocation() != null ? work.getPrimaryLocation().getSource() : null;

        PaperDetailResponseDTO dto = PaperDetailResponseDTO.builder()
                .paperId(java.util.UUID.nameUUIDFromBytes(
                        ("openalex-journal:" + work.getId()).getBytes(java.nio.charset.StandardCharsets.UTF_8)))
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

        // Map authors from OpenAlex authorships
        if (work.getAuthorships() != null && !work.getAuthorships().isEmpty()) {
            List<AuthorDTO> authors = work.getAuthorships().stream()
                    .map(a -> {
                        String name = a.getRawAuthorName();
                        if (name == null && a.getAuthor() != null) {
                            name = a.getAuthor().getDisplayName();
                        }
                        String affiliation = a.getRawAffiliationStrings() != null
                                && !a.getRawAffiliationStrings().isEmpty()
                                ? String.join(", ", a.getRawAffiliationStrings())
                                : (a.getInstitutions() != null && !a.getInstitutions().isEmpty()
                                        ? a.getInstitutions().get(0).getDisplayName() : null);
                        return AuthorDTO.builder()
                                .fullName(name)
                                .affiliation(affiliation)
                                .build();
                    })
                    .collect(Collectors.toList());
            dto.setAuthors(authors);
        }

        // Save to paper cache for persistence
        try { paperCacheService.save(dto, work.getId()); } catch (Exception ignored) {}

        return dto;
    }

    private String rebuildAbstract(Map<String, ? extends List<Integer>> invertedIndex) {
        if (invertedIndex == null || invertedIndex.isEmpty()) return null;
        var entries = new ArrayList<Map.Entry<Integer, String>>();
        for (var e : invertedIndex.entrySet()) {
            var positions = e.getValue();
            if (positions != null) {
                for (int pos : positions) entries.add(new AbstractMap.SimpleEntry<>(pos, e.getKey()));
            }
        }
        entries.sort(Map.Entry.comparingByKey());
        return entries.stream().map(Map.Entry::getValue).collect(Collectors.joining(" "));
    }

    // ── Journal autocomplete from OpenAlex sources API ──

    @Override
    public List<JournalSuggestionResponse> suggestJournals(String query) {
        if (query.isBlank()) return List.of();

        String authParam = (openalexApiKey != null && !openalexApiKey.isBlank())
                ? "&api_key=" + openalexApiKey : "";

        String url = "https://api.openalex.org/sources?search="
                + java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8)
                + "&sort=cited_by_count:desc&per-page=10" + authParam;

        try {
            String rawJson = restTemplate.getForObject(url, String.class);
            if (rawJson == null) return List.of();

            @SuppressWarnings("unchecked")
            var response = objectMapper.readValue(rawJson, java.util.Map.class);
            var results = (java.util.List<java.util.Map<String, Object>>) response.get("results");
            if (results == null || results.isEmpty()) return List.of();

            return results.stream()
                    .map(r -> JournalSuggestionResponse.builder()
                            .id((String) r.get("id"))
                            .name((String) r.get("display_name"))
                            .issn((String) r.get("issn_l"))
                            .publisher((String) r.get("publisher"))
                            .totalWorks(toInt(r.get("works_count")))
                            .totalCitations(toInt(r.get("cited_by_count")))
                            .build())
                    .toList();
        } catch (Exception e) {
            log.warn("Journal suggestion failed for '{}': {}", query, e.getMessage());
            return List.of();
        }
    }

    private static Integer toInt(Object val) {
        if (val instanceof Number n) return n.intValue();
        return null;
    }
}
