package com.sra.journal_tracking.service.impl;

import com.sra.journal_tracking.dto.dashboard.DatabaseStatsResponse;
import com.sra.journal_tracking.dto.sync.BulkSyncProgress;
import com.sra.journal_tracking.dto.sync.OpenAlexResponseDTO;
import com.sra.journal_tracking.dto.sync.SemanticScholarResponseDTO;
import com.sra.journal_tracking.entity.jpa.ApiSource;
import com.sra.journal_tracking.entity.jpa.ApiSource;
import com.sra.journal_tracking.entity.jpa.Author;
import com.sra.journal_tracking.entity.jpa.Journal;
import com.sra.journal_tracking.entity.jpa.Keyword;
import com.sra.journal_tracking.entity.jpa.PaperAuthor;
import com.sra.journal_tracking.entity.jpa.PaperAuthorId;
import com.sra.journal_tracking.entity.jpa.PaperKeyword;
import com.sra.journal_tracking.entity.jpa.PaperKeywordId;
import com.sra.journal_tracking.entity.jpa.ResearchField;
import com.sra.journal_tracking.entity.jpa.ResearchPaper;
import com.sra.journal_tracking.entity.jpa.SyncLog;
import com.sra.journal_tracking.repository.jpa.ApiSourceRepository;
import com.sra.journal_tracking.repository.jpa.AuthorRepository;
import com.sra.journal_tracking.repository.jpa.JournalRepository;
import com.sra.journal_tracking.repository.jpa.KeywordRepository;
import com.sra.journal_tracking.repository.jpa.PaperAuthorRepository;
import com.sra.journal_tracking.repository.jpa.PaperKeywordRepository;
import com.sra.journal_tracking.repository.jpa.ResearchFieldRepository;
import com.sra.journal_tracking.repository.jpa.ResearchPaperRepository;
import com.sra.journal_tracking.repository.jpa.SyncLogRepository;
import com.sra.journal_tracking.service.BulkSyncProgressTracker;
import com.sra.journal_tracking.service.DataSyncService;
import com.sra.journal_tracking.service.GraphService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataSyncServiceImpl implements DataSyncService {
    private static final int RECENT_PUBLICATION_YEAR_WINDOW = 3;
    private static final int MAX_AUTHORS_PER_PAPER = 5;
    private static final int MAX_KEYWORDS_PER_PAPER = 8;
    private static final int MAX_BULK_PAGES_PER_KEYWORD = 50; // safety cap to avoid infinite pagination

    private final ResearchPaperRepository researchPaperRepository;
    private final AuthorRepository authorRepository;
    private final PaperAuthorRepository paperAuthorRepository;
    private final PaperKeywordRepository paperKeywordRepository;
    private final ApiSourceRepository apiSourceRepository;
    private final JournalRepository journalRepository;
    private final ResearchFieldRepository researchFieldRepository;
    private final KeywordRepository keywordRepository;
    private final SyncLogRepository syncLogRepository;
    private final GraphService graphService;
    private final RestTemplate restTemplate;
    private final BulkSyncProgressTracker bulkSyncProgressTracker;

    @PersistenceContext
    private EntityManager entityManager;

    @Value("${app.core-api-key:}")
    private String coreApiKey;

    @Value("${app.openalex-email:}")
    private String openalexEmail;

    @Override
    public SyncLog syncFromOpenAlex(String query, int limit) {
        int currentYear = Year.now().getValue();
        return syncFromOpenAlex(query, limit, currentYear - RECENT_PUBLICATION_YEAR_WINDOW + 1, currentYear);
    }

    @Override
    public SyncLog syncFromSemanticScholar(String query, int limit) {
        int currentYear = Year.now().getValue();
        return syncFromSemanticScholar(query, limit, currentYear - RECENT_PUBLICATION_YEAR_WINDOW + 1, currentYear);
    }

    @Override
    public SyncLog syncFromSemanticScholar(String query, int limit, Integer yearFrom, Integer yearTo) {
        log.info("Starting Semantic Scholar sync for query: {}", query);
        int yrFrom = yearFrom != null ? yearFrom : Year.now().getValue() - RECENT_PUBLICATION_YEAR_WINDOW + 1;
        int yrTo = yearTo != null ? yearTo : Year.now().getValue();

        ApiSource source = apiSourceRepository.findBySourceName("semantic_scholar")
                .map(s -> {
                    // Fix baseUrl if it was stored with /graph/v1 suffix
                    if (s.getBaseUrl() != null && s.getBaseUrl().endsWith("/graph/v1")) {
                        s.setBaseUrl("https://api.semanticscholar.org");
                        apiSourceRepository.save(s);
                    }
                    return s;
                })
                .orElseGet(() -> apiSourceRepository.save(ApiSource.builder()
                        .sourceName("semantic_scholar")
                        .baseUrl("https://api.semanticscholar.org")
                        .rateLimitRpm(100)
                        .isActive(true)
                        .build()));

        SyncLog syncLog = createRunningSyncLog(source, false);

        try {
            String fields = "paperId,title,abstract,year,publicationDate,isOpenAccess,citationCount,authors,externalIds";
            // Semantic Scholar base URL does NOT include /graph/v1
            String baseUrl = source.getBaseUrl().replaceAll("/graph/v1$", "");
            String url = UriComponentsBuilder
                    .fromHttpUrl(baseUrl + "/graph/v1/paper/search")
                    .queryParam("query", query)
                    .queryParam("fields", fields)
                    .queryParam("limit", limit)
                    .queryParam("year", yrFrom + "-" + yrTo)
                    .build().encode().toUriString();

            SemanticScholarResponseDTO response = restTemplate.getForObject(url, SemanticScholarResponseDTO.class);

            if (response != null && response.getData() != null) {
                int insertedCount = 0;
                syncLog.setPapersFetched(response.getData().size());

                for (SemanticScholarResponseDTO.SemanticScholarPaperDTO paperDTO : response.getData()) {
                    String doi = null;
                    if (paperDTO.getExternalIds() != null) {
                        doi = normalizeDoi(paperDTO.getExternalIds().getDOI());
                    }

                    String title = trimToLength(
                            paperDTO.getTitle() != null ? paperDTO.getTitle() : "Untitled",
                            1000);
                    if (isDuplicatePaper(doi, title, paperDTO.getYear())) {
                        continue;
                    }

                    ResearchPaper newPaper = ResearchPaper.builder()
                            .source(source)
                            .title(title)
                            .abstractText(paperDTO.getAbstractText())
                            .doi(doi)
                            .pubYear(paperDTO.getYear())
                            .citationCount(paperDTO.getCitationCount() != null ? paperDTO.getCitationCount() : 0)
                            .isOpenAccess(paperDTO.getIsOpenAccess() != null ? paperDTO.getIsOpenAccess() : false)
                            .build();

                    setPublicationDate(newPaper, paperDTO.getPublicationDate());

                    ResearchPaper savedPaper = researchPaperRepository.save(newPaper);
                    insertedCount++;

                    // Cache paper-keyword links in Neo4j for graph search.
                    savePaperToNeo4j(savedPaper, List.of(), query);

                    if (paperDTO.getAuthors() != null) {
                        int order = 1;
                        for (SemanticScholarResponseDTO.SemanticScholarPaperDTO.AuthorDTO authorDTO : paperDTO.getAuthors()) {
                            Author author = getOrCreateSemanticScholarAuthor(authorDTO, source);
                            savePaperAuthor(savedPaper, author, order++);
                        }
                    }
                }

                markSyncCompleted(syncLog, insertedCount);
                log.info("Semantic Scholar sync completed. Fetched: {}, Inserted: {}",
                        syncLog.getPapersFetched(), insertedCount);
            }
        } catch (Exception e) {
            markSyncFailed(syncLog, e);
        } finally {
            syncLog = finishSync(syncLog, source);
        }

        return syncLog;
    }

    @Override
    public SyncLog syncFromOpenAlex(String query, int limit, Integer yearFrom, Integer yearTo) {
        log.info("Starting OpenAlex sync for query: {}", query);

        int startYear = yearFrom != null ? yearFrom : Year.now().getValue() - RECENT_PUBLICATION_YEAR_WINDOW + 1;
        int endYear = yearTo != null ? yearTo : Year.now().getValue();

        ApiSource source = getOrCreateOpenAlexSource();
        SyncLog syncLog = createRunningSyncLog(source, true);

        try {
            int perPage = Math.min(100, Math.max(1, limit));
            LocalDate today = LocalDate.now();
            String openAlexSearchQuery = normalizeOpenAlexSearchQuery(query);
            String url = withOpenAlexMailto(
                    UriComponentsBuilder
                            .fromHttpUrl(source.getBaseUrl() + "/works")
                            .queryParam("search", openAlexSearchQuery)
                            .queryParam("filter", "from_publication_date:" + startYear + "-01-01,to_publication_date:" + today)
                            .queryParam("sort", "publication_date:desc")
                            .queryParam("per-page", perPage)
                            .queryParam("select", "id,doi,title,display_name,publication_year,publication_date,cited_by_count,abstract_inverted_index,open_access,primary_location,best_oa_location,topics,keywords,authorships")
            ).build().encode().toUriString();

            OpenAlexResponseDTO response = fetchOpenAlexWithRetry(url, OpenAlexResponseDTO.class, query);

            if (response != null && response.getResults() != null) {
                int insertedCount = 0;
                syncLog.setPapersFetched(response.getResults().size());

                for (OpenAlexResponseDTO.OpenAlexWorkDTO work : response.getResults()) {
                    if (!isRecentPublication(work.getPublicationYear(), work.getPublicationDate(), startYear, endYear, today)) {
                        continue;
                    }
                    String abstractText = rebuildAbstract(work.getAbstractInvertedIndex());
                    if (!isOpenAlexWorkRelevant(work, abstractText, query)) {
                        continue;
                    }

                    String doi = normalizeDoi(work.getDoi());
                    String title = trimToLength(resolveTitle(work), 1000);
                    if (isDuplicatePaper(doi, title, work.getPublicationYear())) {
                        continue;
                    }
                    ResearchField field = resolveResearchField(work);

                    ResearchPaper newPaper = ResearchPaper.builder()
                            .source(source)
                            .title(title)
                            .abstractText(abstractText)
                            .doi(doi)
                            .journal(resolveJournal(work, source, field))
                            .field(field)
                            .pubYear(work.getPublicationYear())
                            .citationCount(work.getCitedByCount() != null ? work.getCitedByCount() : 0)
                            .isOpenAccess(work.getOpenAccess() != null && Boolean.TRUE.equals(work.getOpenAccess().getIsOa()))
                            .pdfUrl(resolvePdfUrl(work))
                            .build();

                    setPublicationDate(newPaper, work.getPublicationDate());

                    ResearchPaper savedPaper = researchPaperRepository.save(newPaper);
                    insertedCount++;

                    // Cache paper-keyword links in Neo4j for graph search.
                    List<String> keywords = saveOpenAlexKeywords(savedPaper, work, query);
                    savePaperToNeo4j(savedPaper, keywords, query);

                    if (work.getAuthorships() != null) {
                        int order = 1;
                        for (OpenAlexResponseDTO.Authorship authorship : work.getAuthorships()) {
                            if (order > MAX_AUTHORS_PER_PAPER) {
                                break;
                            }
                            Author author = getOrCreateOpenAlexAuthor(authorship, source);
                            savePaperAuthor(savedPaper, author, order++);
                        }
                    }
                }

                markSyncCompleted(syncLog, insertedCount);
                log.info("OpenAlex sync completed. Fetched: {}, Inserted: {}",
                        syncLog.getPapersFetched(), insertedCount);
            }
        } catch (Exception e) {
            markSyncFailed(syncLog, e);
        } finally {
            syncLog = finishSync(syncLog, source);
        }

        return syncLog;
    }

    @Override
    @Async
    public void syncFromOpenAlexAsync(String query, int limit) {
        try {
            syncFromOpenAlex(query, limit);
        } catch (Exception e) {
            log.warn("Background OpenAlex sync failed for '{}': {}", query, e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  arXiv sync (XML Atom, free)
    // ═══════════════════════════════════════════════════════════

    @Override
    public SyncLog syncFromArxiv(String query, int limit, Integer yearFrom, Integer yearTo) {
        log.info("Starting arXiv sync for query: {}", query);

        int yrFrom = yearFrom != null ? yearFrom : Year.now().getValue() - RECENT_PUBLICATION_YEAR_WINDOW + 1;
        int yrTo = yearTo != null ? yearTo : Year.now().getValue();

        ApiSource source = getOrCreateArxivSource();
        SyncLog syncLog = createRunningSyncLog(source, true);

        try {
            // arXiv uses + as AND/TO operator — cannot use UriComponentsBuilder (encodes + to %2B)
            String searchQuery = "all:" + query.replace(" ", "+") + "+AND+submittedDate:[" + yrFrom + "01010000+TO+" + yrTo + "12312359]";
            String url = source.getBaseUrl() + "/query?search_query=" + searchQuery
                    + "&start=0&max_results=" + Math.min(limit, 100);
            log.debug("arXiv URL: {}", url);

            String xmlResponse = restTemplate.getForObject(url, String.class);
            if (xmlResponse == null || xmlResponse.isBlank()) {
                markSyncCompleted(syncLog, 0);
                return finishSync(syncLog, source);
            }

            List<ParsedPaper> parsedPapers = parseArxivXml(xmlResponse);
            syncLog.setPapersFetched(parsedPapers.size());

            int insertedCount = 0;
            for (ParsedPaper pp : parsedPapers) {
                if (isDuplicatePaper(pp.doi(), pp.title(), pp.pubYear())) {
                    continue;
                }

                ResearchPaper paper = ResearchPaper.builder()
                        .source(source)
                        .title(pp.title())
                        .abstractText(pp.abstractText())
                        .doi(pp.doi())
                        .pubYear(pp.pubYear())
                        .citationCount(0)
                        .isOpenAccess(true)
                        .pdfUrl(pp.pdfUrl())
                        .build();
                setPublicationDate(paper, pp.pubDate());

                ResearchPaper savedPaper = researchPaperRepository.save(paper);
                insertedCount++;

                List<String> keywords = extractKeywordsFromTitle(pp.title());
                savePaperToNeo4j(savedPaper, keywords, query);

                if (pp.authors() != null) {
                    int order = 1;
                    for (String authorName : pp.authors()) {
                        if (order > MAX_AUTHORS_PER_PAPER) break;
                        Author author = authorRepository
                                .findByFullNameAndSource_SourceId(authorName, source.getSourceId())
                                .orElseGet(() -> authorRepository.saveAndFlush(Author.builder()
                                        .source(source).fullName(authorName).build()));
                        savePaperAuthor(savedPaper, author, order++);
                    }
                }
            }

            markSyncCompleted(syncLog, insertedCount);
            log.info("arXiv sync completed. Fetched: {}, Inserted: {}", parsedPapers.size(), insertedCount);
        } catch (Exception e) {
            markSyncFailed(syncLog, e);
        } finally {
            syncLog = finishSync(syncLog, source);
        }
        return syncLog;
    }

    // ═══════════════════════════════════════════════════════════
    //  CORE API sync (JSON, requires API key)
    // ═══════════════════════════════════════════════════════════

    @Override
    public SyncLog syncFromArxiv(String query, int limit) {
        int currentYear = Year.now().getValue();
        return syncFromArxiv(query, limit, currentYear - RECENT_PUBLICATION_YEAR_WINDOW + 1, currentYear);
    }

    @Override
    public SyncLog syncFromCore(String query, int limit) {
        int currentYear = Year.now().getValue();
        return syncFromCore(query, limit, currentYear - RECENT_PUBLICATION_YEAR_WINDOW + 1, currentYear);
    }

    @Override
    public SyncLog syncFromCore(String query, int limit, Integer yearFrom, Integer yearTo) {
        log.info("Starting CORE sync for query: {}", query);

        if (coreApiKey == null || coreApiKey.isBlank()) {
            throw new RuntimeException("CORE_API_KEY not configured. Add it to .env file.");
        }

        int yrFrom = yearFrom != null ? yearFrom : Year.now().getValue() - RECENT_PUBLICATION_YEAR_WINDOW + 1;
        int yrTo = yearTo != null ? yearTo : Year.now().getValue();

        ApiSource source = getOrCreateCoreSource();
        SyncLog syncLog = createRunningSyncLog(source, true);

        try {
            String url = UriComponentsBuilder
                    .fromHttpUrl(source.getBaseUrl() + "/search/works")
                    .queryParam("q", query)
                    .queryParam("limit", Math.min(100, Math.max(1, limit)))
                    .queryParam("yearFilter", yrFrom + "-" + yrTo)
                    .build().encode().toUriString();
            log.debug("CORE URL: {}", url);

            // Use RestTemplate with Authorization header via exchange
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set("Authorization", "Bearer " + coreApiKey);
            org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>(headers);
            var responseEntity = restTemplate.exchange(url,
                    org.springframework.http.HttpMethod.GET, entity, Map.class);

            Map<String, Object> body = responseEntity.getBody();
            if (body == null) {
                markSyncCompleted(syncLog, 0);
                return finishSync(syncLog, source);
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> results = (List<Map<String, Object>>) body.get("results");
            if (results == null || results.isEmpty()) {
                markSyncCompleted(syncLog, 0);
                return finishSync(syncLog, source);
            }

            syncLog.setPapersFetched(results.size());
            int insertedCount = 0;

            for (Map<String, Object> work : results) {
                String title = stringFromMap(work, "title", "Untitled");
                String abstractText = stringFromMap(work, "abstract", null);
                String doi = normalizeDoi(stringFromMap(work, "doi", null));
                Integer pubYear = intFromMap(work, "yearPublished");
                Short pubYearShort = pubYear != null ? pubYear.shortValue() : null;

                if (isDuplicatePaper(doi, truncateTitle(title), pubYearShort)) continue;

                ResearchPaper paper = ResearchPaper.builder()
                        .source(source)
                        .title(truncateTitle(title))
                        .abstractText(abstractText)
                        .doi(doi)
                        .pubYear(pubYearShort)
                        .citationCount(intFromMap(work, "citationCount", 0))
                        .isOpenAccess(true)
                        .pdfUrl(stringFromMap(work, "downloadUrl", null))
                        .build();

                ResearchPaper savedPaper = researchPaperRepository.save(paper);
                insertedCount++;

                List<String> kws = extractKeywordsFromTitle(title);
                savePaperToNeo4j(savedPaper, kws, query);

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> authors = (List<Map<String, Object>>) work.get("authors");
                if (authors != null) {
                    int order = 1;
                    for (Map<String, Object> a : authors) {
                        if (order > MAX_AUTHORS_PER_PAPER) break;
                        String name = stringFromMap(a, "name", "Unknown Author");
                        Author author = authorRepository
                                .findByFullNameAndSource_SourceId(name, source.getSourceId())
                                .orElseGet(() -> authorRepository.saveAndFlush(Author.builder()
                                        .source(source).fullName(name).build()));
                        savePaperAuthor(savedPaper, author, order++);
                    }
                }
            }

            markSyncCompleted(syncLog, insertedCount);
            log.info("CORE sync completed. Fetched: {}, Inserted: {}", results.size(), insertedCount);
        } catch (Exception e) {
            markSyncFailed(syncLog, e);
        } finally {
            syncLog = finishSync(syncLog, source);
        }
        return syncLog;
    }

    @Override
    @Transactional
    public java.util.Map<String, Object> clearAllPapers() {
        log.info("=== CLEAR ALL: Deleting all papers and related data ===");

        // FK dependencies → must delete in this order:
        // 1. BOOKMARK (→ PAPER, KEYWORD)
        // 2. FOLLOW (→ JOURNAL, TOPIC, KEYWORD)
        // 3. PAPER_KEYWORD (→ PAPER, KEYWORD)
        // 4. PAPER_AUTHOR (→ PAPER, AUTHOR)
        // 5. PUBLICATION_TREND (→ TOPIC)
        // 6. RESEARCH_PAPER
        // 7. Orphan KEYWORD, AUTHOR, JOURNAL, RESEARCH_FIELD
        // 8. SYNC_LOG (→ SOURCE)

        int deletedBookmarks = entityManager.createNativeQuery("DELETE FROM BOOKMARK").executeUpdate();
        int deletedFollows = entityManager.createNativeQuery("DELETE FROM FOLLOW").executeUpdate();
        int deletedPaperKeywords = entityManager.createNativeQuery("DELETE FROM PAPER_KEYWORD").executeUpdate();
        int deletedPaperAuthors = entityManager.createNativeQuery("DELETE FROM PAPER_AUTHOR").executeUpdate();
        int deletedTrends = entityManager.createNativeQuery("DELETE FROM PUBLICATION_TREND").executeUpdate();
        int deletedPapers = entityManager.createNativeQuery("DELETE FROM RESEARCH_PAPER").executeUpdate();
        int deletedOrphanKeywords = entityManager.createNativeQuery(
                "DELETE FROM KEYWORD WHERE KeywordID NOT IN (SELECT DISTINCT KeywordID FROM PAPER_KEYWORD)").executeUpdate();
        int deletedOrphanAuthors = entityManager.createNativeQuery(
                "DELETE FROM AUTHOR WHERE AuthorID NOT IN (SELECT DISTINCT AuthorID FROM PAPER_AUTHOR)").executeUpdate();
        int deletedSyncLogs = entityManager.createNativeQuery("DELETE FROM SYNC_LOG").executeUpdate();

        // Clear Neo4j graph
        boolean neo4jCleared = true;
        try {
            graphService.clearAll();
        } catch (Exception e) {
            log.warn("Neo4j clear failed: {}", e.getMessage());
            neo4jCleared = false;
        }

        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("deletedPapers", deletedPapers);
        result.put("deletedPaperKeywords", deletedPaperKeywords);
        result.put("deletedPaperAuthors", deletedPaperAuthors);
        result.put("deletedOrphanKeywords", deletedOrphanKeywords);
        result.put("deletedOrphanAuthors", deletedOrphanAuthors);
        result.put("deletedBookmarks", deletedBookmarks);
        result.put("deletedFollows", deletedFollows);
        result.put("deletedTrends", deletedTrends);
        result.put("deletedSyncLogs", deletedSyncLogs);
        result.put("neo4jCleared", neo4jCleared);

        log.info("=== CLEAR ALL DONE: {} papers deleted, Neo4j cleared: {} ===", deletedPapers, neo4jCleared);
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public DatabaseStatsResponse getDatabaseStats() {
        // Papers
        long totalPapers = researchPaperRepository.count();
        long openAccess = entityManager
                .createQuery("SELECT COUNT(p) FROM ResearchPaper p WHERE p.isOpenAccess = true", Long.class)
                .getSingleResult();
        long hasPdf = entityManager
                .createQuery("SELECT COUNT(p) FROM ResearchPaper p WHERE p.pdfUrl IS NOT NULL AND p.pdfUrl <> ''", Long.class)
                .getSingleResult();

        // Papers by source
        @SuppressWarnings("unchecked")
        List<Object[]> bySourceRows = entityManager
                .createQuery("SELECT s.sourceName, COUNT(p) FROM ResearchPaper p JOIN p.source s GROUP BY s.sourceName")
                .getResultList();
        Map<String, Long> bySource = new LinkedHashMap<>();
        for (Object[] row : bySourceRows) {
            bySource.put((String) row[0], (Long) row[1]);
        }

        // Papers by year
        @SuppressWarnings("unchecked")
        List<Object[]> byYearRows = entityManager
                .createQuery("SELECT p.pubYear, COUNT(p) FROM ResearchPaper p WHERE p.pubYear IS NOT NULL GROUP BY p.pubYear ORDER BY p.pubYear DESC")
                .getResultList();
        Map<Integer, Long> byYear = new LinkedHashMap<>();
        for (Object[] row : byYearRows) {
            byYear.put(((Short) row[0]).intValue(), (Long) row[1]);
        }

        // Authors
        long totalAuthors = authorRepository.count();
        long orphanedAuthors = ((Number) entityManager
                .createNativeQuery("SELECT COUNT(*) FROM AUTHOR WHERE AuthorID NOT IN (SELECT DISTINCT AuthorID FROM PAPER_AUTHOR)")
                .getSingleResult()).longValue();

        // Keywords
        long totalKeywords = keywordRepository.count();
        long orphanedKeywords = ((Number) entityManager
                .createNativeQuery("SELECT COUNT(*) FROM KEYWORD WHERE KeywordID NOT IN (SELECT DISTINCT KeywordID FROM PAPER_KEYWORD)")
                .getSingleResult()).longValue();

        // Journals & Fields
        long totalJournals = journalRepository.count();
        long totalFields = researchFieldRepository.count();

        // Topics
        long totalTopics = ((Number) entityManager
                .createNativeQuery("SELECT COUNT(*) FROM RESEARCH_TOPIC")
                .getSingleResult()).longValue();
        long trending = ((Number) entityManager
                .createNativeQuery("SELECT COUNT(*) FROM RESEARCH_TOPIC WHERE IsTrending = 1")
                .getSingleResult()).longValue();

        // Sync logs
        long totalSyncLogs = syncLogRepository.count();
        String lastSync = null;
        try {
            Object lastSyncObj = entityManager
                    .createQuery("SELECT MAX(s.completedAt) FROM SyncLog s WHERE s.completedAt IS NOT NULL")
                    .getSingleResult();
            if (lastSyncObj != null) lastSync = lastSyncObj.toString();
        } catch (Exception ignored) {}

        // Neo4j
        DatabaseStatsResponse.Neo4jStats neo4jStats = getNeo4jStats();

        return DatabaseStatsResponse.builder()
                .papers(DatabaseStatsResponse.PaperStats.builder()
                        .total(totalPapers).bySource(bySource).byYear(byYear)
                        .openAccess(openAccess).hasPdfUrl(hasPdf).build())
                .authors(DatabaseStatsResponse.AuthorStats.builder()
                        .total(totalAuthors).orphaned(orphanedAuthors).build())
                .keywords(DatabaseStatsResponse.KeywordStats.builder()
                        .total(totalKeywords).orphaned(orphanedKeywords).build())
                .journals(DatabaseStatsResponse.CountStat.builder().total(totalJournals).build())
                .researchFields(DatabaseStatsResponse.CountStat.builder().total(totalFields).build())
                .researchTopics(DatabaseStatsResponse.TopicStats.builder()
                        .total(totalTopics).trending(trending).build())
                .neo4j(neo4jStats)
                .syncLogs(DatabaseStatsResponse.SyncStats.builder()
                        .total(totalSyncLogs).lastSync(lastSync).build())
                .build();
    }

    private DatabaseStatsResponse.Neo4jStats getNeo4jStats() {
        Map<String, Long> stats = graphService.getStats();
        return DatabaseStatsResponse.Neo4jStats.builder()
                .paperNodes(stats.getOrDefault("paperNodes", 0L))
                .keywordNodes(stats.getOrDefault("keywordNodes", 0L))
                .relationships(stats.getOrDefault("relationships", 0L))
                .build();
    }

    // ═══════════════════════════════════════════════════════════
    //  Bulk sync (OpenAlex pagination)
    // ═══════════════════════════════════════════════════════════

    @Override
    public Map<String, Object> bulkSyncFromOpenAlex(List<String> keywords, int papersPerKeyword, Integer yearFrom, Integer yearTo) {
        log.info("Starting BULK sync: {} keywords, {} papers each", keywords.size(), papersPerKeyword);

        int yrFrom = yearFrom != null ? yearFrom : Year.now().getValue() - 3;
        int yrTo = yearTo != null ? yearTo : Year.now().getValue();
        int perPage = Math.min(200, Math.max(10, papersPerKeyword));
        int perKeyword = Math.max(1, papersPerKeyword);

        ApiSource source = getOrCreateOpenAlexSource();
        LocalDate today = LocalDate.now();
        int totalFetched = 0, totalInserted = 0;
        Map<String, Map<String, Integer>> keywordStats = new LinkedHashMap<>();

        for (String keyword : keywords) {
            int kwScanned = 0, kwInserted = 0, pageCount = 0;
            int skippedByYear = 0, skippedByRelevance = 0, skippedByDuplicate = 0;
            String nextCursor = "*";
            boolean hasMore = true;

            // Stop when: (1) enough papers inserted, (2) no more pages, or (3) safety cap reached
            while (hasMore && kwInserted < perKeyword && pageCount < MAX_BULK_PAGES_PER_KEYWORD) {
                try {
                    String url = withOpenAlexMailto(
                            UriComponentsBuilder
                                    .fromHttpUrl(source.getBaseUrl() + "/works")
                                    .queryParam("search", normalizeOpenAlexSearchQuery(keyword))
                                    .queryParam("filter", "from_publication_date:" + yrFrom + "-01-01,to_publication_date:" + today)
                                    .queryParam("sort", "publication_date:desc")
                                    .queryParam("per-page", perPage)
                                    .queryParam("cursor", nextCursor)
                                    .queryParam("select", "id,doi,title,display_name,publication_year,publication_date,cited_by_count,abstract_inverted_index,open_access,primary_location,best_oa_location,topics,keywords,authorships")
                    ).build().encode().toUriString();

                    OpenAlexResponseDTO response = fetchOpenAlexWithRetry(url, OpenAlexResponseDTO.class, keyword);
                    pageCount++;

                    if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
                        hasMore = false;
                        break;
                    }

                    for (OpenAlexResponseDTO.OpenAlexWorkDTO work : response.getResults()) {
                        if (kwInserted >= perKeyword) break;
                        kwScanned++;

                        if (!isRecentPublication(work.getPublicationYear(), work.getPublicationDate(), yrFrom, yrTo, today)) {
                            skippedByYear++;
                            continue;
                        }

                        String abstractText = rebuildAbstract(work.getAbstractInvertedIndex());
                        if (!isOpenAlexWorkRelevant(work, abstractText, keyword)) {
                            skippedByRelevance++;
                            continue;
                        }

                        String doi = normalizeDoi(work.getDoi());
                        String title = trimToLength(resolveTitle(work), 1000);
                        if (isDuplicatePaper(doi, title, work.getPublicationYear())) {
                            skippedByDuplicate++;
                            continue;
                        }

                        ResearchField field = resolveResearchField(work);
                        ResearchPaper newPaper = ResearchPaper.builder()
                                .source(source)
                                .title(title)
                                .abstractText(abstractText)
                                .doi(doi)
                                .journal(resolveJournal(work, source, field))
                                .field(field)
                                .pubYear(work.getPublicationYear())
                                .citationCount(work.getCitedByCount() != null ? work.getCitedByCount() : 0)
                                .isOpenAccess(work.getOpenAccess() != null && Boolean.TRUE.equals(work.getOpenAccess().getIsOa()))
                                .pdfUrl(resolvePdfUrl(work))
                                .build();
                        setPublicationDate(newPaper, work.getPublicationDate());

                        ResearchPaper savedPaper = researchPaperRepository.save(newPaper);
                        kwInserted++;

                        List<String> kws = saveOpenAlexKeywords(savedPaper, work, keyword);
                        savePaperToNeo4j(savedPaper, kws, keyword);

                        if (work.getAuthorships() != null) {
                            int order = 1;
                            for (OpenAlexResponseDTO.Authorship authorship : work.getAuthorships()) {
                                if (order > MAX_AUTHORS_PER_PAPER) break;
                                Author author = getOrCreateOpenAlexAuthor(authorship, source);
                                savePaperAuthor(savedPaper, author, order++);
                            }
                        }
                    }

                    // Pagination
                    if (response.getMeta() != null && response.getMeta().getNextCursor() != null) {
                        nextCursor = response.getMeta().getNextCursor();
                    } else {
                        hasMore = false;
                    }

                    Thread.sleep(500); // Rate limit
                } catch (Exception e) {
                    log.warn("Bulk sync page failed for '{}': {}", keyword, e.getMessage());
                    hasMore = false;
                }
            }

            totalFetched += kwScanned;
            totalInserted += kwInserted;
            keywordStats.put(keyword, Map.of("scanned", kwScanned, "inserted", kwInserted));
            log.info("Bulk sync: '{}' → scanned {}, inserted {} (pages: {}, skipped: year={} relevance={} dup={})",
                    keyword, kwScanned, kwInserted, pageCount, skippedByYear, skippedByRelevance, skippedByDuplicate);
        }

        log.info("BULK SYNC DONE: {} keywords, {} total fetched, {} total inserted",
                keywords.size(), totalFetched, totalInserted);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalKeywords", keywords.size());
        result.put("totalFetched", totalFetched);
        result.put("totalInserted", totalInserted);
        result.put("yearRange", yrFrom + "-" + yrTo);
        result.put("keywordStats", keywordStats);
        return result;
    }

    @Override
    @Async("taskExecutor")
    public void bulkSyncFromOpenAlexAsync(String taskId, List<String> keywords, int papersPerKeyword, Integer yearFrom, Integer yearTo) {
        try {
            Map<String, Object> result = bulkSyncFromOpenAlexWithProgress(taskId, keywords, papersPerKeyword, yearFrom, yearTo);
            bulkSyncProgressTracker.markCompleted(taskId, result);
        } catch (Exception e) {
            log.error("Bulk sync task {} failed: {}", taskId, e.getMessage(), e);
            bulkSyncProgressTracker.markFailed(taskId, e.getMessage());
        }
    }

    /**
     * Same logic as {@link #bulkSyncFromOpenAlex(List, int, Integer, Integer)} but updates
     * progress in {@link BulkSyncProgressTracker} after each keyword.
     */
    private Map<String, Object> bulkSyncFromOpenAlexWithProgress(String taskId, List<String> keywords,
                                                                  int papersPerKeyword, Integer yearFrom, Integer yearTo) {
        log.info("Starting BULK sync [{}]: {} keywords, {} papers each", taskId, keywords.size(), papersPerKeyword);

        int yrFrom = yearFrom != null ? yearFrom : Year.now().getValue() - 3;
        int yrTo = yearTo != null ? yearTo : Year.now().getValue();
        int perPage = Math.min(200, Math.max(10, papersPerKeyword));
        int perKeyword = Math.max(1, papersPerKeyword);

        ApiSource source = getOrCreateOpenAlexSource();
        LocalDate today = LocalDate.now();
        int totalFetched = 0, totalInserted = 0;
        Map<String, Map<String, Integer>> keywordStats = new LinkedHashMap<>();

        // Register the task for progress tracking (already created in controller, but call here for safety)
        if (bulkSyncProgressTracker.getProgress(taskId) == null) {
            bulkSyncProgressTracker.createTask(taskId, keywords.size());
        }

        for (String keyword : keywords) {
            int kwScanned = 0, kwInserted = 0, pageCount = 0;
            int skippedByYear = 0, skippedByRelevance = 0, skippedByDuplicate = 0;
            String nextCursor = "*";
            boolean hasMore = true;

            // Stop when: (1) enough papers inserted, (2) no more pages, or (3) safety cap reached
            while (hasMore && kwInserted < perKeyword && pageCount < MAX_BULK_PAGES_PER_KEYWORD) {
                try {
                    String url = withOpenAlexMailto(
                            UriComponentsBuilder
                                    .fromHttpUrl(source.getBaseUrl() + "/works")
                                    .queryParam("search", normalizeOpenAlexSearchQuery(keyword))
                                    .queryParam("filter", "from_publication_date:" + yrFrom + "-01-01,to_publication_date:" + today)
                                    .queryParam("sort", "publication_date:desc")
                                    .queryParam("per-page", perPage)
                                    .queryParam("cursor", nextCursor)
                                    .queryParam("select", "id,doi,title,display_name,publication_year,publication_date,cited_by_count,abstract_inverted_index,open_access,primary_location,best_oa_location,topics,keywords,authorships")
                    ).build().encode().toUriString();

                    OpenAlexResponseDTO response = fetchOpenAlexWithRetry(url, OpenAlexResponseDTO.class, keyword);
                    pageCount++;

                    if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
                        hasMore = false;
                        break;
                    }

                    for (OpenAlexResponseDTO.OpenAlexWorkDTO work : response.getResults()) {
                        if (kwInserted >= perKeyword) break;
                        kwScanned++;

                        if (!isRecentPublication(work.getPublicationYear(), work.getPublicationDate(), yrFrom, yrTo, today)) {
                            skippedByYear++;
                            continue;
                        }

                        String abstractText = rebuildAbstract(work.getAbstractInvertedIndex());
                        if (!isOpenAlexWorkRelevant(work, abstractText, keyword)) {
                            skippedByRelevance++;
                            continue;
                        }

                        String doi = normalizeDoi(work.getDoi());
                        String title = trimToLength(resolveTitle(work), 1000);
                        if (isDuplicatePaper(doi, title, work.getPublicationYear())) {
                            skippedByDuplicate++;
                            continue;
                        }

                        ResearchField field = resolveResearchField(work);
                        ResearchPaper newPaper = ResearchPaper.builder()
                                .source(source)
                                .title(title)
                                .abstractText(abstractText)
                                .doi(doi)
                                .journal(resolveJournal(work, source, field))
                                .field(field)
                                .pubYear(work.getPublicationYear())
                                .citationCount(work.getCitedByCount() != null ? work.getCitedByCount() : 0)
                                .isOpenAccess(work.getOpenAccess() != null && Boolean.TRUE.equals(work.getOpenAccess().getIsOa()))
                                .pdfUrl(resolvePdfUrl(work))
                                .build();
                        setPublicationDate(newPaper, work.getPublicationDate());

                        ResearchPaper savedPaper = researchPaperRepository.save(newPaper);
                        kwInserted++;

                        List<String> kws = saveOpenAlexKeywords(savedPaper, work, keyword);
                        savePaperToNeo4j(savedPaper, kws, keyword);

                        if (work.getAuthorships() != null) {
                            int order = 1;
                            for (OpenAlexResponseDTO.Authorship authorship : work.getAuthorships()) {
                                if (order > MAX_AUTHORS_PER_PAPER) break;
                                Author author = getOrCreateOpenAlexAuthor(authorship, source);
                                savePaperAuthor(savedPaper, author, order++);
                            }
                        }
                    }

                    // Pagination
                    if (response.getMeta() != null && response.getMeta().getNextCursor() != null) {
                        nextCursor = response.getMeta().getNextCursor();
                    } else {
                        hasMore = false;
                    }

                    Thread.sleep(500); // Rate limit
                } catch (Exception e) {
                    String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    log.warn("Bulk sync [{}] page failed for '{}': {}", taskId, keyword, errorMsg);
                    log.debug("Bulk sync [{}] stack trace for '{}'", taskId, keyword, e);
                    bulkSyncProgressTracker.addKeywordError(taskId, keyword, errorMsg);
                    hasMore = false;
                }
            }

            totalFetched += kwScanned;
            totalInserted += kwInserted;
            keywordStats.put(keyword, Map.of("scanned", kwScanned, "inserted", kwInserted));

            // Update progress after each keyword
            bulkSyncProgressTracker.updateKeywordProgress(taskId, keyword, kwScanned, kwInserted, totalFetched, totalInserted);

            log.info("Bulk sync [{}]: '{}' → scanned {}, inserted {} (pages: {}, {}%, skipped: year={} relevance={} dup={})",
                    taskId, keyword, kwScanned, kwInserted, pageCount,
                    (keywordStats.size() * 100) / keywords.size(),
                    skippedByYear, skippedByRelevance, skippedByDuplicate);
        }

        log.info("BULK SYNC [{}] DONE: {} keywords, {} total fetched, {} total inserted",
                taskId, keywords.size(), totalFetched, totalInserted);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalKeywords", keywords.size());
        result.put("totalFetched", totalFetched);
        result.put("totalInserted", totalInserted);
        result.put("yearRange", yrFrom + "-" + yrTo);
        result.put("keywordStats", keywordStats);
        return result;
    }

    private SyncLog createRunningSyncLog(ApiSource source, boolean isManual) {
        SyncLog syncLog = SyncLog.builder()
                .source(source)
                .syncType("incremental")
                .isManual(isManual)
                .status("running")
                .startedAt(LocalDateTime.now())
                .papersFetched(0)
                .papersInserted(0)
                .build();
        return syncLogRepository.save(syncLog);
    }

    private void markSyncCompleted(SyncLog syncLog, int insertedCount) {
        syncLog.setPapersInserted(insertedCount);
        syncLog.setStatus("completed");
    }

    private void markSyncFailed(SyncLog syncLog, Exception e) {
        log.error("Error during sync", e);
        syncLog.setStatus("failed");
        syncLog.setErrorMessage(e.getMessage());
    }

    private SyncLog finishSync(SyncLog syncLog, ApiSource source) {
        syncLog.setCompletedAt(LocalDateTime.now());
        SyncLog savedSyncLog = syncLogRepository.save(syncLog);

        source.setLastSyncedAt(LocalDateTime.now());
        apiSourceRepository.save(source);

        return savedSyncLog;
    }

    private ApiSource getOrCreateOpenAlexSource() {
        return apiSourceRepository.findBySourceName("openalex")
                .orElseGet(() -> apiSourceRepository.save(ApiSource.builder()
                        .sourceName("openalex")
                        .baseUrl("https://api.openalex.org")
                        .rateLimitRpm(100)
                        .isActive(true)
                        .build()));
    }

    private ApiSource getOrCreateArxivSource() {
        return apiSourceRepository.findBySourceName("arxiv")
                .orElseGet(() -> apiSourceRepository.save(ApiSource.builder()
                        .sourceName("arxiv")
                        .baseUrl("http://export.arxiv.org/api")
                        .rateLimitRpm(30)
                        .isActive(true)
                        .build()));
    }

    private ApiSource getOrCreateCoreSource() {
        return apiSourceRepository.findBySourceName("core")
                .orElseGet(() -> apiSourceRepository.save(ApiSource.builder()
                        .sourceName("core")
                        .baseUrl("https://api.core.ac.uk/v3")
                        .rateLimitRpm(60)
                        .isActive(true)
                        .build()));
    }

    private Author getOrCreateSemanticScholarAuthor(
            SemanticScholarResponseDTO.SemanticScholarPaperDTO.AuthorDTO authorDTO,
            ApiSource source) {
        String fullName = authorDTO.getName() != null ? authorDTO.getName() : "Unknown Author";

        if (authorDTO.getAuthorId() == null) {
            // Without an external ID, reuse by name first to avoid duplicate NULL IDs.
            return authorRepository.findByFullNameAndSource_SourceId(fullName, source.getSourceId())
                    .orElseGet(() -> authorRepository.saveAndFlush(Author.builder()
                            .source(source)
                            .fullName(fullName)
                            .build()));
        }

        return authorRepository.findByExternalAuthorIdAndSource_SourceId(authorDTO.getAuthorId(), source.getSourceId())
                .orElseGet(() -> authorRepository.saveAndFlush(Author.builder()
                        .source(source)
                        .externalAuthorId(authorDTO.getAuthorId())
                        .fullName(fullName)
                        .build()));
    }

    private Author getOrCreateOpenAlexAuthor(OpenAlexResponseDTO.Authorship authorship, ApiSource source) {
        String externalAuthorId = authorship.getAuthor() != null ? authorship.getAuthor().getId() : null;
        String fullName = resolveAuthorName(authorship);
        String affiliation = resolveAffiliation(authorship.getRawAffiliationStrings());

        if (externalAuthorId == null) {
            // Without an external ID, reuse by name first to avoid duplicate NULL IDs.
            return authorRepository.findByFullNameAndSource_SourceId(fullName, source.getSourceId())
                    .orElseGet(() -> authorRepository.saveAndFlush(Author.builder()
                            .source(source)
                            .fullName(fullName)
                            .affiliation(affiliation)
                            .build()));
        }

        return authorRepository.findByExternalAuthorIdAndSource_SourceId(externalAuthorId, source.getSourceId())
                .orElseGet(() -> authorRepository.saveAndFlush(Author.builder()
                        .source(source)
                        .externalAuthorId(externalAuthorId)
                        .fullName(fullName)
                        .affiliation(affiliation)
                        .build()));
    }

    private void savePaperAuthor(ResearchPaper savedPaper, Author author, int authorOrder) {
        PaperAuthorId id = new PaperAuthorId(savedPaper.getPaperId(), author.getAuthorId());
        if (paperAuthorRepository.existsById(id)) {
            return;
        }

        PaperAuthor paperAuthor = PaperAuthor.builder()
                .id(id)
                .paper(savedPaper)
                .author(author)
                .authorOrder(authorOrder)
                .build();
        paperAuthorRepository.save(paperAuthor);
    }

    private void setPublicationDate(ResearchPaper paper, String publicationDate) {
        if (publicationDate == null) {
            return;
        }

        try {
            paper.setPubDate(LocalDate.parse(publicationDate));
        } catch (Exception e) {
            log.warn("Invalid publication date: {}", publicationDate);
        }
    }

    private String resolveTitle(OpenAlexResponseDTO.OpenAlexWorkDTO work) {
        if (work.getTitle() != null && !work.getTitle().isBlank()) {
            return work.getTitle();
        }
        if (work.getDisplayName() != null && !work.getDisplayName().isBlank()) {
            return work.getDisplayName();
        }
        return "Untitled";
    }

    private String resolveAuthorName(OpenAlexResponseDTO.Authorship authorship) {
        if (authorship.getAuthor() != null
                && authorship.getAuthor().getDisplayName() != null
                && !authorship.getAuthor().getDisplayName().isBlank()) {
            return authorship.getAuthor().getDisplayName();
        }
        if (authorship.getRawAuthorName() != null && !authorship.getRawAuthorName().isBlank()) {
            return authorship.getRawAuthorName();
        }
        return "Unknown Author";
    }

    private boolean isRecentPublication(Short publicationYear, String publicationDate, int startYear, int currentYear, LocalDate today) {
        // Fallback: extract year from publicationDate if publicationYear is null
        if (publicationYear == null) {
            if (publicationDate != null && publicationDate.length() >= 4) {
                try {
                    publicationYear = (short) Integer.parseInt(publicationDate.substring(0, 4));
                } catch (NumberFormatException e) {
                    return false;
                }
            } else {
                return false;
            }
        }
        if (publicationYear < startYear || publicationYear > currentYear) {
            return false;
        }
        if (publicationDate == null || publicationDate.isBlank()) {
            return true;
        }
        try {
            return !LocalDate.parse(publicationDate).isAfter(today);
        } catch (Exception e) {
            return true;
        }
    }

    private boolean isOpenAlexWorkRelevant(OpenAlexResponseDTO.OpenAlexWorkDTO work, String abstractText, String query) {
        List<String> tokens = extractSearchTokens(query);
        if (tokens.isEmpty()) {
            return true;
        }

        StringBuilder primaryText = new StringBuilder();
        append(primaryText, work.getTitle());
        append(primaryText, work.getDisplayName());

        if (!isInstitutionNoiseQuery(query)) {
            OpenAlexResponseDTO.Source source = work.getPrimaryLocation() != null
                    ? work.getPrimaryLocation().getSource()
                    : null;
            if (source != null) {
                append(primaryText, source.getDisplayName());
                append(primaryText, source.getPublisher());
                append(primaryText, source.getHostOrganizationName());
            }
        }

        if (work.getTopics() != null) {
            work.getTopics().forEach(topic -> {
                append(primaryText, topic.getDisplayName());
                if (topic.getField() != null) {
                    append(primaryText, topic.getField().getDisplayName());
                }
                if (topic.getDomain() != null) {
                    append(primaryText, topic.getDomain().getDisplayName());
                }
            });
        }

        if (work.getKeywords() != null) {
            work.getKeywords().forEach(keyword -> append(primaryText, keyword.getDisplayName()));
        }

        String normalizedPrimaryText = normalizeSearchText(primaryText.toString());
        if (tokens.size() == 1) {
            // Single-token: check both primary text AND abstract to avoid
            // filtering out papers that use synonyms (e.g., "automobile" vs "car")
            StringBuilder fullText = new StringBuilder(normalizedPrimaryText);
            append(fullText, abstractText);
            return containsTokenVariant(normalizeSearchText(fullText.toString()), tokens.get(0));
        }

        StringBuilder fullText = new StringBuilder(normalizedPrimaryText);
        append(fullText, abstractText);
        String normalizedFullText = normalizeSearchText(fullText.toString());
        return tokens.stream().allMatch(token -> containsTokenVariant(normalizedFullText, token))
                && tokens.stream().anyMatch(token -> containsTokenVariant(normalizedPrimaryText, token));
    }

    private List<String> extractSearchTokens(String query) {
        String normalized = normalizeSearchText(query);
        if (normalized.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(normalized.split(" "))
                .filter(token -> token.length() >= 2)
                .filter(token -> !isSearchStopWord(token))
                .distinct()
                .toList();
    }

    private boolean isSearchStopWord(String token) {
        return token.equals("and") || token.equals("or") || token.equals("the") || token.equals("of")
                || token.equals("in") || token.equals("on") || token.equals("for") || token.equals("to")
                || token.equals("a") || token.equals("an");
    }

    private boolean isInstitutionNoiseQuery(String query) {
        String normalizedQuery = normalizeSearchText(query);
        return normalizedQuery.equals("university")
                || normalizedQuery.equals("college")
                || normalizedQuery.equals("repository")
                || normalizedQuery.equals("journal")
                || normalizedQuery.equals("institute")
                || normalizedQuery.equals("institution");
    }

    private boolean containsTokenVariant(String text, String token) {
        return tokenVariants(token).stream().anyMatch(variant -> containsNormalizedTerm(text, variant));
    }

    private boolean containsNormalizedTerm(String text, String term) {
        String normalizedText = normalizeSearchText(text);
        String normalizedTerm = normalizeSearchText(term);
        if (normalizedText.isBlank() || normalizedTerm.isBlank()) {
            return false;
        }
        return (" " + normalizedText + " ").contains(" " + normalizedTerm + " ");
    }

    private List<String> tokenVariants(String token) {
        List<String> variants = new ArrayList<>();
        variants.add(token);
        if ("phenomenon".equals(token)) {
            variants.add("phenomena");
        } else if ("phenomena".equals(token)) {
            variants.add("phenomenon");
        }
        if (token.endsWith("y") && token.length() > 3) {
            variants.add(token.substring(0, token.length() - 1) + "ies");
        } else if (token.endsWith("ies") && token.length() > 4) {
            variants.add(token.substring(0, token.length() - 3) + "y");
        } else if (token.endsWith("s") && token.length() > 3) {
            variants.add(token.substring(0, token.length() - 1));
        } else if (token.length() > 3) {
            variants.add(token + "s");
        }
        return variants.stream().distinct().toList();
    }

    private String normalizeSearchText(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase()
                .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private void append(StringBuilder text, String value) {
        if (value != null && !value.isBlank()) {
            text.append(' ').append(value);
        }
    }

    private String resolveAffiliation(List<String> affiliations) {
        if (affiliations == null || affiliations.isEmpty()) {
            return null;
        }

        String affiliation = affiliations.get(0);
        if (affiliation == null || affiliation.isBlank()) {
            return null;
        }
        return affiliation.length() > 500 ? affiliation.substring(0, 500) : affiliation;
    }

    private Journal resolveJournal(OpenAlexResponseDTO.OpenAlexWorkDTO work, ApiSource source, ResearchField field) {
        OpenAlexResponseDTO.Source openAlexSource = work.getPrimaryLocation() != null
                ? work.getPrimaryLocation().getSource()
                : null;
        if (openAlexSource == null || isBlank(openAlexSource.getDisplayName())) {
            return null;
        }

        String issn = trimToLength(openAlexSource.getIssnL(), 20);
        if (!isBlank(issn)) {
            return journalRepository.findByIssn(issn)
                    .orElseGet(() -> createJournal(openAlexSource, source, field, issn));
        }

        String journalName = trimToLength(openAlexSource.getDisplayName(), 500);
        return journalRepository.findByJournalNameIgnoreCase(journalName)
                .orElseGet(() -> createJournal(openAlexSource, source, field, null));
    }

    private Journal createJournal(OpenAlexResponseDTO.Source openAlexSource, ApiSource source, ResearchField field, String issn) {
        return journalRepository.save(Journal.builder()
                .source(source)
                .field(field)
                .journalName(trimToLength(openAlexSource.getDisplayName(), 500))
                .issn(issn)
                .publisher(trimToLength(firstNonBlank(openAlexSource.getPublisher(), openAlexSource.getHostOrganizationName()), 300))
                .isActive(true)
                .build());
    }

    private ResearchField resolveResearchField(OpenAlexResponseDTO.OpenAlexWorkDTO work) {
        String fieldName = null;
        if (work.getTopics() != null && !work.getTopics().isEmpty()) {
            OpenAlexResponseDTO.Topic topic = work.getTopics().get(0);
            if (topic.getField() != null && !isBlank(topic.getField().getDisplayName())) {
                fieldName = topic.getField().getDisplayName();
            } else if (topic.getDomain() != null && !isBlank(topic.getDomain().getDisplayName())) {
                fieldName = topic.getDomain().getDisplayName();
            } else {
                fieldName = topic.getDisplayName();
            }
        }
        return getOrCreateResearchField(fieldName);
    }

    private ResearchField getOrCreateResearchField(String rawFieldName) {
        if (isBlank(rawFieldName)) {
            return null;
        }
        String fieldName = trimToLength(rawFieldName, 200);
        return researchFieldRepository.findByFieldNameIgnoreCase(fieldName)
                .orElseGet(() -> researchFieldRepository.save(ResearchField.builder()
                        .fieldName(fieldName)
                        .isTracked(true)
                        .description("Imported from OpenAlex")
                        .build()));
    }

    private List<String> saveOpenAlexKeywords(ResearchPaper paper, OpenAlexResponseDTO.OpenAlexWorkDTO work, String query) {
        Map<String, KeywordCandidate> candidates = new LinkedHashMap<>();

        if (work.getKeywords() != null) {
            for (OpenAlexResponseDTO.Keyword keyword : work.getKeywords()) {
                addKeywordCandidate(candidates, keyword.getDisplayName(), keyword.getScore());
            }
        }

        if (work.getTopics() != null) {
            for (OpenAlexResponseDTO.Topic topic : work.getTopics()) {
                addKeywordCandidate(candidates, topic.getDisplayName(), topic.getScore());
            }
        }

        List<String> savedKeywordTexts = new ArrayList<>();
        for (KeywordCandidate candidate : candidates.values()) {
            Keyword keyword = keywordRepository.findByNormalizedText(candidate.normalizedText())
                    .orElseGet(() -> keywordRepository.save(Keyword.builder()
                            .field(paper.getField())
                            .keywordText(candidate.keywordText())
                            .normalizedText(candidate.normalizedText())
                            .paperCount(0)
                            .build()));

            keyword.setPaperCount((keyword.getPaperCount() != null ? keyword.getPaperCount() : 0) + 1);
            keywordRepository.save(keyword);

            PaperKeywordId id = new PaperKeywordId(paper.getPaperId(), keyword.getKeywordId());
            if (!paperKeywordRepository.existsById(id)) {
                paperKeywordRepository.save(PaperKeyword.builder()
                        .id(id)
                        .paper(paper)
                        .keyword(keyword)
                        .relevanceScore(candidate.score())
                        .build());
            }

            savedKeywordTexts.add(candidate.keywordText());
            if (savedKeywordTexts.size() >= MAX_KEYWORDS_PER_PAPER) {
                break;
            }
        }

        return savedKeywordTexts;
    }

    private String normalizeOpenAlexSearchQuery(String query) {
        if (query == null) {
            return "";
        }
        String normalized = query
                .replace("&", " ")
                .replace("/", " ")
                .replace("\\", " ")
                .trim()
                .replaceAll("\\s+", " ");
        return normalized.isEmpty() ? query.trim() : normalized;
    }

    private void addKeywordCandidate(Map<String, KeywordCandidate> candidates, String rawText, Double score) {
        if (isBlank(rawText)) {
            return;
        }
        String keywordText = trimToLength(rawText.trim(), 300);
        String normalizedText = normalizeKeyword(keywordText);
        if (isBlank(normalizedText)) {
            return;
        }
        candidates.putIfAbsent(normalizedText, new KeywordCandidate(keywordText, normalizedText, score != null ? score : 1.0));
    }

    private String normalizeKeyword(String value) {
        return trimToLength(value.toLowerCase().trim().replaceAll("\\s+", " "), 300);
    }

    private String firstNonBlank(String first, String second) {
        return !isBlank(first) ? first : second;
    }

    private String resolvePdfUrl(OpenAlexResponseDTO.OpenAlexWorkDTO work) {
        if (work.getBestOaLocation() != null && !isBlank(work.getBestOaLocation().getPdfUrl())) {
            return work.getBestOaLocation().getPdfUrl();
        }
        if (work.getPrimaryLocation() != null && !isBlank(work.getPrimaryLocation().getPdfUrl())) {
            return work.getPrimaryLocation().getPdfUrl();
        }
        if (work.getOpenAccess() != null && !isBlank(work.getOpenAccess().getOaUrl())) {
            return work.getOpenAccess().getOaUrl();
        }
        return null;
    }

    private String trimToLength(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean isDuplicatePaper(String doi, String title, Short publicationYear) {
        if (!isBlank(doi) && researchPaperRepository.findByDoi(doi).isPresent()) {
            return true;
        }
        if (isBlank(title)) {
            return false;
        }
        return researchPaperRepository.countDuplicateByTitleAndYear(title, publicationYear) > 0;
    }

    // ═══════════════════════════════════════════════════════════
    //  OpenAlex API helpers
    // ═══════════════════════════════════════════════════════════

    /**
     * Add mailto param to OpenAlex URL builder if email is configured.
     * This puts requests in the "polite pool" with much higher rate limits.
     */
    private UriComponentsBuilder withOpenAlexMailto(UriComponentsBuilder builder) {
        if (openalexEmail != null && !openalexEmail.isBlank()) {
            builder.queryParam("mailto", openalexEmail);
        }
        return builder;
    }

    /**
     * Call OpenAlex API with retry on 429 rate limit.
     */
    @SuppressWarnings("unchecked")
    private <T> T fetchOpenAlexWithRetry(String url, Class<T> responseType, String context) {
        int maxRetries = 3;
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                return restTemplate.getForObject(url, responseType);
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : "";
                boolean isRateLimited = msg.contains("429") || msg.contains("Rate limit") || msg.contains("Too Many Requests");
                if (isRateLimited && attempt < maxRetries - 1) {
                    long waitMs = (attempt + 1) * 3000L;
                    log.warn("OpenAlex 429 rate limited for '{}', waiting {}s (attempt {}/{})",
                            context, waitMs / 1000, attempt + 1, maxRetries);
                    try { Thread.sleep(waitMs); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                } else {
                    throw new RuntimeException("OpenAlex API error for '" + context + "': " + msg, e);
                }
            }
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════════
    //  arXiv XML parser + shared helpers
    // ═══════════════════════════════════════════════════════════

    private List<ParsedPaper> parseArxivXml(String xml) {
        List<ParsedPaper> papers = new ArrayList<>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            InputStream is = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
            Document doc = builder.parse(is);
            NodeList entries = doc.getElementsByTagName("entry");

            for (int i = 0; i < entries.getLength(); i++) {
                Element entry = (Element) entries.item(i);
                String title = textByTag(entry, "title");
                String summary = textByTag(entry, "summary");
                String arxivId = textByTag(entry, "id");
                if (arxivId != null && arxivId.contains("/abs/")) {
                    arxivId = arxivId.substring(arxivId.lastIndexOf('/') + 1);
                }
                String published = textByTag(entry, "published");
                String doi = textByTagNS(entry, "http://arxiv.org/schemas/atom", "doi");
                Short pubYear = null;
                String pubDate = null;
                if (published != null) {
                    try {
                        pubDate = published.substring(0, 10);
                        pubYear = (short) Integer.parseInt(published.substring(0, 4));
                    } catch (Exception ignored) {}
                }

                // Extract PDF URL from <link title="pdf">
                String pdfUrl = null;
                NodeList links = entry.getElementsByTagName("link");
                for (int j = 0; j < links.getLength(); j++) {
                    Element link = (Element) links.item(j);
                    if ("pdf".equals(link.getAttribute("title"))) {
                        pdfUrl = link.getAttribute("href");
                        break;
                    }
                }

                // Extract authors
                List<String> authors = new ArrayList<>();
                NodeList authorNodes = entry.getElementsByTagName("author");
                for (int j = 0; j < authorNodes.getLength(); j++) {
                    Element authorEl = (Element) authorNodes.item(j);
                    String name = textByTag(authorEl, "name");
                    if (name != null && !name.isBlank()) authors.add(name.trim());
                }

                String finalTitle = truncateTitle(title != null ? title : "Untitled");
                papers.add(new ParsedPaper(finalTitle, summary, doi, pubYear, pubDate, authors, pdfUrl));
            }
        } catch (Exception e) {
            log.error("Failed to parse arXiv XML: {}", e.getMessage());
        }
        return papers;
    }

    private String textByTag(Element parent, String tagName) {
        NodeList list = parent.getElementsByTagName(tagName);
        if (list.getLength() > 0) {
            return list.item(0).getTextContent();
        }
        return null;
    }

    private String textByTagNS(Element parent, String namespace, String tagName) {
        NodeList list = parent.getElementsByTagNameNS(namespace, tagName);
        if (list.getLength() > 0) {
            return list.item(0).getTextContent();
        }
        return null;
    }

    private List<String> extractKeywordsFromTitle(String title) {
        if (title == null) return List.of();
        String[] words = title.toLowerCase().replaceAll("[^a-z0-9 ]", " ").trim().split("\\s+");
        return java.util.Arrays.stream(words)
                .filter(w -> w.length() > 3)
                .distinct()
                .limit(MAX_KEYWORDS_PER_PAPER)
                .collect(Collectors.toList());
    }

    private String truncateTitle(String title) {
        return trimToLength(title, 1000);
    }

    private String stringFromMap(Map<String, Object> map, String key, String defaultValue) {
        Object val = map.get(key);
        return val != null ? val.toString() : defaultValue;
    }

    private Integer intFromMap(Map<String, Object> map, String key, Integer defaultValue) {
        Object val = map.get(key);
        if (val instanceof Number) return ((Number) val).intValue();
        if (val != null) {
            try { return Integer.parseInt(val.toString()); } catch (NumberFormatException e) {}
        }
        return defaultValue;
    }

    private Integer intFromMap(Map<String, Object> map, String key) {
        return intFromMap(map, key, null);
    }

    private record ParsedPaper(String title, String abstractText, String doi,
                               Short pubYear, String pubDate, List<String> authors, String pdfUrl) {
    }

    private record KeywordCandidate(String keywordText, String normalizedText, Double score) {
    }

    private String rebuildAbstract(Map<String, List<Integer>> invertedIndex) {
        if (invertedIndex == null || invertedIndex.isEmpty()) {
            return null;
        }

        return invertedIndex.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream()
                        .map(position -> Map.entry(position, entry.getKey())))
                .sorted(Comparator.comparingInt(Map.Entry::getKey))
                .map(Map.Entry::getValue)
                .reduce((left, right) -> left + " " + right)
                .orElse(null);
    }

    /**
     * Save paper and its keywords to Neo4j for graph search.
     * Always includes the search query as a keyword so the paper
     * is discoverable via the exact term the user searched for.
     */
    private void savePaperToNeo4j(ResearchPaper paper, List<String> keywords, String searchQuery) {
        try {
            List<String> graphKeywords = new ArrayList<>();
            // Always add the search query first so graph search finds it
            if (searchQuery != null && !searchQuery.isBlank()) {
                graphKeywords.add(searchQuery.trim());
            }
            if (keywords != null) {
                keywords.stream()
                        .filter(kw -> kw != null && !kw.isBlank())
                        .forEach(graphKeywords::add);
            }
            if (graphKeywords.isEmpty()) {
                graphKeywords.add(paper.getTitle() != null ? paper.getTitle() : "Untitled");
            }
            graphService.savePaperWithKeywords(
                    paper.getPaperId().toString(),
                    paper.getTitle(),
                    paper.getDoi(),
                    paper.getPubYear() != null ? paper.getPubYear().intValue() : null,
                    graphKeywords
            );
        } catch (Exception e) {
            log.warn("Neo4j save skipped for paper {}: {}", paper.getPaperId(), e.getMessage());
            log.warn("Full stack trace:", e);
        }
    }

    private String normalizeDoi(String doi) {
        if (doi == null || doi.isBlank()) {
            return null;
        }

        String normalized = doi.trim()
                .replaceFirst("(?i)^https?://doi\\.org/", "")
                .replaceFirst("(?i)^doi:", "");
        if (normalized.isBlank()) {
            return null;
        }
        return trimToLength(normalized, 200);
    }

}
