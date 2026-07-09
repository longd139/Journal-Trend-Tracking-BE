package com.sra.journal_tracking.service.impl;

import com.sra.journal_tracking.dto.dashboard.DatabaseStatsResponse;
import com.sra.journal_tracking.dto.sync.BulkSyncProgress;
import com.sra.journal_tracking.dto.sync.OpenAlexResponseDTO;
import com.sra.journal_tracking.dto.sync.SemanticScholarResponseDTO;
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
import com.sra.journal_tracking.repository.jpa.ResearchTopicRepository;
import com.sra.journal_tracking.repository.jpa.ResearchPaperRepository;
import com.sra.journal_tracking.repository.jpa.SyncLogRepository;
import com.sra.journal_tracking.service.BulkSyncProgressTracker;
import com.sra.journal_tracking.service.DataSyncService;
import com.sra.journal_tracking.service.GraphService;
import com.sra.journal_tracking.service.KeywordExtractionService;
import com.sra.journal_tracking.service.NotificationTriggerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
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
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataSyncServiceImpl implements DataSyncService {

    /**
     * Generate a deterministic UUID from an OpenAlex work, based on its ID URL.
     * The same OpenAlex work always produces the same UUID — across databases, servers, and restarts.
     * Used by both sync (saveOpenAlexWork) and search preview (stablePreviewId) so IDs match.
     */
    public static UUID generatePaperIdFromOpenAlexWork(OpenAlexResponseDTO.OpenAlexWorkDTO work) {
        String key = work.getId() != null ? work.getId() : work.getDoi();
        if (key == null) key = work.getTitle() != null ? work.getTitle() : "";
        return UUID.nameUUIDFromBytes(("openalex:" + key).getBytes(StandardCharsets.UTF_8));
    }

    private static final int RECENT_PUBLICATION_YEAR_WINDOW = 3;
    private static final int MAX_AUTHORS_PER_PAPER = 5;
    private static final int MAX_KEYWORDS_PER_PAPER = 8;
    private static final int MAX_BULK_PAGES_PER_KEYWORD = 200; // safety cap (200 pages × 200 per page = 40k papers max per keyword)

    // Rate limit tracking — shared across all keywords to avoid 429
    // CORE: 60 rpm → target 50 rpm (1200ms interval) for safety margin
    // OpenAlex: 100k/day for authenticated, but polite pool ~100 rpm → target 90 rpm (666ms)
    // Semantic Scholar: 100 req/5min without key → 1 req/3s → target 3500ms for safety
    // arXiv: 1 req/3s recommended → target 3500ms interval (strict but safe)
    private static final long CORE_MIN_INTERVAL_MS = 1200;
    private static final long OPENALEX_MIN_INTERVAL_MS = 700;
    private static final long SEMANTIC_SCHOLAR_MIN_INTERVAL_MS = 3500;
    private static final long ARXIV_MIN_INTERVAL_MS = 3500;
    private volatile long lastCoreRequestTime = 0;
    private volatile long lastOpenAlexRequestTime = 0;
    private volatile long lastSemanticScholarRequestTime = 0;
    private volatile long lastArxivRequestTime = 0;

    private final ResearchPaperRepository researchPaperRepository;
    private final AuthorRepository authorRepository;
    private final PaperAuthorRepository paperAuthorRepository;
    private final PaperKeywordRepository paperKeywordRepository;
    private final ApiSourceRepository apiSourceRepository;
    private final JournalRepository journalRepository;
    private final ResearchFieldRepository researchFieldRepository;
    private final KeywordRepository keywordRepository;
    private final ResearchTopicRepository researchTopicRepository;
    private final SyncLogRepository syncLogRepository;
    private final GraphService graphService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final BulkSyncProgressTracker bulkSyncProgressTracker;
    private final KeywordExtractionService keywordExtractionService;
    private final NotificationTriggerService notificationTriggerService;

    @PersistenceContext
    private EntityManager entityManager;

    @Lazy
    @Autowired
    private DataSyncServiceImpl self;

    @Value("${app.core-api-key:}")
    private String coreApiKey;

    @Value("${app.semantic-scholar-api-key:}")
    private String semanticScholarApiKey;

    @Value("${app.openalex-email:}")
    private String openalexEmail;

    @Value("${app.openalex-api-key:}")
    private String openalexApiKey;

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

                    // Trigger notification for users following related journal/keyword
                    notificationTriggerService.notifyNewPaper(savedPaper);

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
                    ResearchPaper savedPaper = saveOpenAlexWork(work, source, query, startYear, endYear, today, false);
                    if (savedPaper != null) {
                        insertedCount++;
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

    @Override
    public SyncLog syncPapersFromOpenAlexByAuthor(String openAlexAuthorId, String authorName, int limit) {
        log.info("Starting OpenAlex sync by author ID: {} ({})", authorName, openAlexAuthorId);

        ApiSource source = getOrCreateOpenAlexSource();
        SyncLog syncLog = createRunningSyncLog(source, true);

        try {
            int perPage = Math.min(100, Math.max(1, limit));
            int startYear = Year.now().getValue() - RECENT_PUBLICATION_YEAR_WINDOW + 1;
            int endYear = Year.now().getValue();
            LocalDate today = LocalDate.now();

            // Filter by author ID — no text search needed
            String url = withOpenAlexMailto(
                    UriComponentsBuilder
                            .fromHttpUrl(source.getBaseUrl() + "/works")
                            .queryParam("filter", "authorships.author.id:" + openAlexAuthorId
                                    + ",from_publication_date:" + startYear + "-01-01,to_publication_date:" + today)
                            .queryParam("sort", "publication_date:desc")
                            .queryParam("per-page", perPage)
                            .queryParam("select", "id,doi,title,display_name,publication_year,publication_date,cited_by_count,abstract_inverted_index,open_access,primary_location,best_oa_location,topics,keywords,authorships")
            ).build().encode().toUriString();

            OpenAlexResponseDTO response = fetchOpenAlexWithRetry(url, OpenAlexResponseDTO.class, authorName);

            if (response != null && response.getResults() != null) {
                int insertedCount = 0;
                syncLog.setPapersFetched(response.getResults().size());

                for (OpenAlexResponseDTO.OpenAlexWorkDTO work : response.getResults()) {
                    // Use authorName as "query" for keyword/Neo4j indexing
                    ResearchPaper savedPaper = saveOpenAlexWork(work, source, authorName, startYear, endYear, today, true);
                    if (savedPaper != null) {
                        insertedCount++;
                    }
                }

                markSyncCompleted(syncLog, insertedCount);
                log.info("OpenAlex author sync completed for '{}'. Fetched: {}, Inserted: {}",
                        authorName, syncLog.getPapersFetched(), insertedCount);
            }
        } catch (Exception e) {
            markSyncFailed(syncLog, e);
        } finally {
            syncLog = finishSync(syncLog, source);
        }

        return syncLog;
    }

    /**
     * Save a single OpenAlex work to the local database.
     * Extracted from syncFromOpenAlex so it can be reused by syncPapersFromOpenAlexByAuthor.
     *
     * @return the saved ResearchPaper, or null if the work was skipped (not recent, duplicate, etc.)
     */
    private ResearchPaper saveOpenAlexWork(OpenAlexResponseDTO.OpenAlexWorkDTO work,
                                           ApiSource source,
                                           String query,
                                           int startYear,
                                           int endYear,
                                           LocalDate today,
                                           boolean skipRelevance) {
        if (!isRecentPublication(work.getPublicationYear(), work.getPublicationDate(), startYear, endYear, today)) {
            return null;
        }

        String abstractText = rebuildAbstract(work.getAbstractInvertedIndex());

        // Skip relevance when filtering by author ID (author name won't appear in paper text)
        if (!skipRelevance && !isOpenAlexWorkRelevant(work, abstractText, query)) {
            return null;
        }

        String doi = normalizeDoi(work.getDoi());
        String title = trimToLength(resolveTitle(work), 1000);

        // Use deterministic UUID from OpenAlex work URL — same ID whether from sync or search preview
        UUID paperId = generatePaperIdFromOpenAlexWork(work);

        // If paper with this deterministic ID already exists, skip
        if (researchPaperRepository.existsById(paperId)) {
            return null;
        }

        // If paper exists by DOI with a DIFFERENT ID (old random UUID), delete and re-insert
        if (!isBlank(doi) && researchPaperRepository.findByDoi(doi).isPresent()) {
            var oldPaper = researchPaperRepository.findByDoi(doi).get();
            if (!oldPaper.getPaperId().equals(paperId)) {
                log.info("Re-syncing paper '{}' from {} to {} (deterministic UUID)", title,
                        oldPaper.getPaperId(), paperId);
                // Delete old paper (cascades PAPER_AUTHOR, PAPER_KEYWORD — they get recreated below)
                researchPaperRepository.delete(oldPaper);
                entityManager.flush();
            }
        }

        ResearchField field = resolveResearchField(work);

        ResearchPaper newPaper = ResearchPaper.builder()
                .paperId(paperId)
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
                .type(work.getType())
                .openAlexWorkId(work.getId())
                .build();

        setPublicationDate(newPaper, work.getPublicationDate());

        ResearchPaper savedPaper = researchPaperRepository.save(newPaper);

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

        // Trigger notification for users following related journal/keyword
        notificationTriggerService.notifyNewPaper(savedPaper);

        return savedPaper;
    }

    @Override
    public void saveSingleWorkFromOpenAlex(OpenAlexResponseDTO.OpenAlexWorkDTO work) {
        try {
            ApiSource source = getOrCreateOpenAlexSource();
            // Use wide year range to accept all papers (top-papers search includes old papers)
            int startYear = 1900;
            int endYear = Year.now().getValue();
            LocalDate today = LocalDate.now();
            // Use wide year range + skip relevance to accept all papers
            saveOpenAlexWork(work, source, "fallback-cache", startYear, endYear, today, true);
        } catch (Exception e) {
            log.warn("Failed to save fallback paper from OpenAlex: {}", e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  arXiv sync (XML Atom, free)
    // ═══════════════════════════════════════════════════════════

    @Override
    @Async("taskExecutor")
    public void triggerManualSyncAsync(String sourceName, String query, int limit, Integer yearFrom, Integer yearTo) {
        try {
            SyncLog syncLog = switch (sourceName.toLowerCase()) {
                case "openalex" -> syncFromOpenAlex(query, limit, yearFrom, yearTo);
                case "semantic_scholar" -> syncFromSemanticScholar(query, limit, yearFrom, yearTo);
                case "arxiv" -> syncFromArxiv(query, limit, yearFrom, yearTo);
                case "core" -> syncFromCore(query, limit, yearFrom, yearTo);
                default -> throw new IllegalArgumentException("Unsupported sync source: " + sourceName);
            };
            if (!Boolean.TRUE.equals(syncLog.getIsManual())) {
                syncLog.setIsManual(true);
                syncLogRepository.save(syncLog);
            }
        } catch (Exception e) {
            log.warn("Manual background sync failed for source '{}' and query '{}': {}",
                    sourceName, query, e.getMessage());
        }
    }

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

                // Trigger notification for users following related journal/keyword
                notificationTriggerService.notifyNewPaper(savedPaper);

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

                // Trigger notification for users following related journal/keyword
                notificationTriggerService.notifyNewPaper(savedPaper);

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
    public java.util.Map<String, Object> bulkSyncFromCore(String taskId, java.util.List<String> keywords,
                                                           int papersPerKeyword, Integer yearFrom, Integer yearTo,
                                                           String apiKey) {
        log.info("Starting CORE BULK sync [{}]: {} keywords, {} papers each", taskId, keywords.size(), papersPerKeyword);

        // Resolve API key: use provided key or fall back to configured key
        String resolvedApiKey = (apiKey != null && !apiKey.isBlank()) ? apiKey : coreApiKey;
        if (resolvedApiKey == null || resolvedApiKey.isBlank()) {
            throw new RuntimeException("CORE_API_KEY not configured. Add it to .env file or pass apiKey parameter.");
        }

        int yrFrom = yearFrom != null ? yearFrom : 1900;
        int yrTo = yearTo != null ? yearTo : Year.now().getValue();
        int perPage = Math.min(100, Math.max(10, papersPerKeyword));
        int perKeyword = Math.max(1, papersPerKeyword);

        ApiSource source = getOrCreateCoreSource();
        int totalFetched = 0, totalInserted = 0;
        java.util.Map<String, java.util.Map<String, Integer>> keywordStats = new LinkedHashMap<>();

        // Ensure task is registered in progress tracker
        if (bulkSyncProgressTracker.getProgress(taskId) == null) {
            bulkSyncProgressTracker.createTask(taskId, keywords.size());
        }

        // Prepare reusable HTTP entity with auth header
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.set("Authorization", "Bearer " + resolvedApiKey);
        org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>(headers);

        for (String keyword : keywords) {
            int kwScanned = 0, kwInserted = 0, pageCount = 0;
            int skippedByYear = 0, skippedByDuplicate = 0;
            int offset = 0;
            boolean hasMore = true;

            while (hasMore && kwInserted < perKeyword && pageCount < MAX_BULK_PAGES_PER_KEYWORD) {
                try {
                    String url = UriComponentsBuilder
                            .fromHttpUrl(source.getBaseUrl() + "/search/works")
                            .queryParam("q", keyword)
                            .queryParam("limit", perPage)
                            .queryParam("offset", offset)
                            .queryParam("yearFilter", yrFrom + "-" + yrTo)
                            .build().encode().toUriString();
                    log.debug("CORE bulk [{}] '{}' page {}: {}", taskId, keyword, pageCount + 1, url);

                    rateLimitCore(); // ensure minimum interval before every API call
                    java.util.Map<String, Object> body = fetchCoreWithRetry(url, entity, keyword);
                    pageCount++;

                    if (body == null) {
                        hasMore = false;
                        break;
                    }

                    @SuppressWarnings("unchecked")
                    java.util.List<java.util.Map<String, Object>> results =
                            (java.util.List<java.util.Map<String, Object>>) body.get("results");
                    if (results == null || results.isEmpty()) {
                        hasMore = false;
                        break;
                    }

                    // Immediate UI update: show fetched count before processing
                    int fetchedThisPage = results.size();
                    bulkSyncProgressTracker.updatePageProgress(taskId, keyword,
                            kwScanned + fetchedThisPage, kwInserted,
                            totalFetched + kwScanned + fetchedThisPage,
                            totalInserted + kwInserted);

                    for (java.util.Map<String, Object> work : results) {
                        if (kwInserted >= perKeyword) break;
                        kwScanned++;

                        String title = stringFromMap(work, "title", "Untitled");
                        String abstractText = stringFromMap(work, "abstract", null);
                        String doi = normalizeDoi(stringFromMap(work, "doi", null));
                        Integer pubYear = intFromMap(work, "yearPublished");
                        Short pubYearShort = pubYear != null ? pubYear.shortValue() : null;

                        // Year filter (double-check — CORE yearFilter is not always strict)
                        if (pubYearShort != null && (pubYearShort < yrFrom || pubYearShort > yrTo)) {
                            skippedByYear++;
                            continue;
                        }

                        if (isDuplicatePaper(doi, truncateTitle(title), pubYearShort)) {
                            skippedByDuplicate++;
                            continue;
                        }

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
                        kwInserted++;

                        java.util.List<String> kws = extractKeywordsFromTitle(title);
                        savePaperToNeo4j(savedPaper, kws, keyword);

                        // Trigger notification for users following related journal/keyword
                        notificationTriggerService.notifyNewPaper(savedPaper);

                        @SuppressWarnings("unchecked")
                        java.util.List<java.util.Map<String, Object>> authors =
                                (java.util.List<java.util.Map<String, Object>>) work.get("authors");
                        if (authors != null) {
                            int order = 1;
                            for (java.util.Map<String, Object> a : authors) {
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

                    // CORE pagination: check totalHits to determine if more pages exist
                    Object totalHitsObj = body.get("totalHits");
                    int totalHits = totalHitsObj instanceof Number
                            ? ((Number) totalHitsObj).intValue() : 0;
                    offset += perPage;
                    if (offset >= totalHits) {
                        hasMore = false;
                    }

                    // Log progress every 5 pages
                    if (pageCount % 5 == 0) {
                        log.info("CORE bulk [{}] '{}': page {}, inserted {} so far (scanned: {}, totalHits: {})",
                                taskId, keyword, pageCount, kwInserted, kwScanned, totalHits);
                    }

                    // Update progress tracker after each page for real-time UI
                    bulkSyncProgressTracker.updatePageProgress(taskId, keyword, kwScanned, kwInserted,
                            totalFetched + kwScanned, totalInserted + kwInserted);

                    rateLimitCore();
                } catch (Exception e) {
                    String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    log.warn("CORE bulk [{}] page failed for '{}': {}", taskId, keyword, errorMsg);
                    bulkSyncProgressTracker.addKeywordError(taskId, keyword, errorMsg);
                    hasMore = false;
                }
            }

            totalFetched += kwScanned;
            totalInserted += kwInserted;
            keywordStats.put(keyword, java.util.Map.of(
                    "scanned", kwScanned, "inserted", kwInserted,
                    "skippedByYear", skippedByYear, "skippedByDuplicate", skippedByDuplicate));

            // Update progress after each keyword
            bulkSyncProgressTracker.updateKeywordProgress(taskId, keyword, kwScanned, kwInserted,
                    totalFetched, totalInserted);

            log.info("CORE bulk [{}]: '{}' → scanned {}, inserted {} (pages: {}, {}%, skipped: year={} dup={})",
                    taskId, keyword, kwScanned, kwInserted, pageCount,
                    (keywordStats.size() * 100) / keywords.size(),
                    skippedByYear, skippedByDuplicate);
        }

        log.info("CORE BULK SYNC [{}] DONE: {} keywords, {} total fetched, {} total inserted",
                taskId, keywords.size(), totalFetched, totalInserted);

        java.util.Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalKeywords", keywords.size());
        result.put("totalFetched", totalFetched);
        result.put("totalInserted", totalInserted);
        result.put("yearRange", yrFrom + "-" + yrTo);
        result.put("keywordStats", keywordStats);

        // Pre-compute fresh stats in background so GET /stats is always instant
        self.refreshStatsCache();

        return result;
    }

    @Override
    @Async("taskExecutor")
    public void bulkSyncFromCoreAsync(String taskId, java.util.List<String> keywords,
                                       int papersPerKeyword, Integer yearFrom, Integer yearTo,
                                       String apiKey) {
        try {
            java.util.Map<String, Object> result = bulkSyncFromCore(
                    taskId, keywords, papersPerKeyword, yearFrom, yearTo, apiKey);
            bulkSyncProgressTracker.markCompleted(taskId, result);
            log.info("CORE async bulk sync [{}] completed: {} papers inserted across {} keywords",
                    taskId, result.getOrDefault("totalInserted", 0), keywords.size());
        } catch (Exception e) {
            log.error("CORE async bulk sync [{}] failed: {}", taskId, e.getMessage(), e);
            bulkSyncProgressTracker.markFailed(taskId, e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Semantic Scholar bulk sync
    // ═══════════════════════════════════════════════════════════

    @Override
    public java.util.Map<String, Object> bulkSyncFromSemanticScholar(String taskId,
                                                                      java.util.List<String> keywords,
                                                                      int papersPerKeyword, Integer yearFrom,
                                                                      Integer yearTo, String apiKey) {
        // Resolve API key: use provided key or fall back to configured key
        String resolvedApiKey = (apiKey != null && !apiKey.isBlank()) ? apiKey : semanticScholarApiKey;
        boolean hasKey = resolvedApiKey != null && !resolvedApiKey.isBlank();

        log.info("Starting Semantic Scholar BULK sync [{}]: {} keywords, {} papers each (API key: {})",
                taskId, keywords.size(), papersPerKeyword, hasKey ? "yes" : "no");

        int yrFrom = yearFrom != null ? yearFrom : 1900;
        int yrTo = yearTo != null ? yearTo : Year.now().getValue();
        int perPage = Math.min(100, Math.max(10, papersPerKeyword));
        int perKeyword = Math.max(1, papersPerKeyword);

        ApiSource source = getOrCreateSemanticScholarSource();
        int totalFetched = 0, totalInserted = 0;
        java.util.Map<String, java.util.Map<String, Integer>> keywordStats = new LinkedHashMap<>();

        if (bulkSyncProgressTracker.getProgress(taskId) == null) {
            bulkSyncProgressTracker.createTask(taskId, keywords.size());
        }

        String fields = "paperId,title,abstract,year,publicationDate,isOpenAccess,citationCount,authors,externalIds";
        String baseUrl = source.getBaseUrl().replaceAll("/graph/v1$", "");

        // Prepare headers with API key if available
        org.springframework.http.HttpHeaders headers = null;
        org.springframework.http.HttpEntity<String> entity = null;
        if (hasKey) {
            headers = new org.springframework.http.HttpHeaders();
            headers.set("x-api-key", resolvedApiKey);
            entity = new org.springframework.http.HttpEntity<>(headers);
        }

        for (String keyword : keywords) {
            int kwScanned = 0, kwInserted = 0, pageCount = 0;
            int skippedByYear = 0, skippedByDuplicate = 0;
            int offset = 0;
            boolean hasMore = true;

            while (hasMore && kwInserted < perKeyword && pageCount < MAX_BULK_PAGES_PER_KEYWORD) {
                try {
                    String url = UriComponentsBuilder
                            .fromHttpUrl(baseUrl + "/graph/v1/paper/search")
                            .queryParam("query", keyword)
                            .queryParam("fields", fields)
                            .queryParam("limit", perPage)
                            .queryParam("offset", offset)
                            .queryParam("year", yrFrom + "-" + yrTo)
                            .build().encode().toUriString();

                    // Dynamic rate limit: 500ms with key, 3500ms without
                    if (hasKey) {
                        rateLimitOpenAlex(); // reuse 700ms interval for authenticated S2
                    } else {
                        rateLimitSemanticScholar(); // 3500ms for unauthenticated
                    }

                    SemanticScholarResponseDTO response = fetchSemanticScholarWithRetry(url, keyword, entity);
                    pageCount++;

                    if (response == null || response.getData() == null || response.getData().isEmpty()) {
                        hasMore = false;
                        break;
                    }

                    // Immediate UI update
                    int fetchedThisPage = response.getData().size();
                    bulkSyncProgressTracker.updatePageProgress(taskId, keyword,
                            kwScanned + fetchedThisPage, kwInserted,
                            totalFetched + kwScanned + fetchedThisPage,
                            totalInserted + kwInserted);

                    for (SemanticScholarResponseDTO.SemanticScholarPaperDTO paperDTO : response.getData()) {
                        if (kwInserted >= perKeyword) break;
                        kwScanned++;

                        String doi = paperDTO.getExternalIds() != null
                                ? normalizeDoi(paperDTO.getExternalIds().getDOI()) : null;

                        if (paperDTO.getYear() != null && (paperDTO.getYear() < yrFrom || paperDTO.getYear() > yrTo)) {
                            skippedByYear++;
                            continue;
                        }

                        String title = trimToLength(paperDTO.getTitle() != null ? paperDTO.getTitle() : "Untitled", 1000);
                        if (isDuplicatePaper(doi, title, paperDTO.getYear())) {
                            skippedByDuplicate++;
                            continue;
                        }

                        ResearchPaper newPaper = ResearchPaper.builder()
                                .source(source).title(title)
                                .abstractText(paperDTO.getAbstractText()).doi(doi)
                                .pubYear(paperDTO.getYear())
                                .citationCount(paperDTO.getCitationCount() != null ? paperDTO.getCitationCount() : 0)
                                .isOpenAccess(paperDTO.getIsOpenAccess() != null ? paperDTO.getIsOpenAccess() : false)
                                .build();
                        setPublicationDate(newPaper, paperDTO.getPublicationDate());

                        ResearchPaper savedPaper = researchPaperRepository.save(newPaper);
                        kwInserted++;

                        savePaperToNeo4j(savedPaper, java.util.List.of(), keyword);
                        notificationTriggerService.notifyNewPaper(savedPaper);

                        if (paperDTO.getAuthors() != null) {
                            int order = 1;
                            for (SemanticScholarResponseDTO.SemanticScholarPaperDTO.AuthorDTO a : paperDTO.getAuthors()) {
                                if (order > MAX_AUTHORS_PER_PAPER) break;
                                Author author = getOrCreateSemanticScholarAuthor(a, source);
                                savePaperAuthor(savedPaper, author, order++);
                            }
                        }
                    }

                    // Pagination
                    if (response.getTotal() != null && offset + perPage < response.getTotal()) {
                        offset += perPage;
                    } else {
                        hasMore = false;
                    }

                    if (pageCount % 5 == 0) {
                        log.info("S2 bulk [{}] '{}': page {}, inserted {} (scanned: {}, total: {})",
                                taskId, keyword, pageCount, kwInserted, kwScanned, response.getTotal());
                    }

                    bulkSyncProgressTracker.updatePageProgress(taskId, keyword, kwScanned, kwInserted,
                            totalFetched + kwScanned, totalInserted + kwInserted);
                } catch (Exception e) {
                    String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    log.warn("S2 bulk [{}] page failed for '{}': {}", taskId, keyword, errorMsg);
                    bulkSyncProgressTracker.addKeywordError(taskId, keyword, errorMsg);
                    hasMore = false;
                }
            }

            totalFetched += kwScanned;
            totalInserted += kwInserted;
            keywordStats.put(keyword, java.util.Map.of(
                    "scanned", kwScanned, "inserted", kwInserted,
                    "skippedByYear", skippedByYear, "skippedByDuplicate", skippedByDuplicate));
            bulkSyncProgressTracker.updateKeywordProgress(taskId, keyword, kwScanned, kwInserted,
                    totalFetched, totalInserted);

            log.info("S2 bulk [{}]: '{}' → scanned {}, inserted {} (pages: {}, {}%)",
                    taskId, keyword, kwScanned, kwInserted, pageCount,
                    (keywordStats.size() * 100) / keywords.size());
        }

        log.info("S2 BULK SYNC [{}] DONE: {} keywords, {} total fetched, {} total inserted",
                taskId, keywords.size(), totalFetched, totalInserted);

        java.util.Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalKeywords", keywords.size());
        result.put("totalFetched", totalFetched);
        result.put("totalInserted", totalInserted);
        result.put("yearRange", yrFrom + "-" + yrTo);
        result.put("keywordStats", keywordStats);
        self.refreshStatsCache();
        return result;
    }

    @Override
    @Async("taskExecutor")
    public void bulkSyncFromSemanticScholarAsync(String taskId, java.util.List<String> keywords,
                                                  int papersPerKeyword, Integer yearFrom, Integer yearTo,
                                                  String apiKey) {
        try {
            java.util.Map<String, Object> result = bulkSyncFromSemanticScholar(
                    taskId, keywords, papersPerKeyword, yearFrom, yearTo, apiKey);
            bulkSyncProgressTracker.markCompleted(taskId, result);
            log.info("S2 async bulk sync [{}] completed: {} papers", taskId,
                    result.getOrDefault("totalInserted", 0));
        } catch (Exception e) {
            log.error("S2 async bulk sync [{}] failed: {}", taskId, e.getMessage(), e);
            bulkSyncProgressTracker.markFailed(taskId, e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  arXiv bulk sync (XML Atom feed)
    // ═══════════════════════════════════════════════════════════

    @Override
    public java.util.Map<String, Object> bulkSyncFromArxiv(String taskId,
                                                            java.util.List<String> keywords,
                                                            int papersPerKeyword, Integer yearFrom,
                                                            Integer yearTo) {
        log.info("Starting arXiv BULK sync [{}]: {} keywords, {} papers each",
                taskId, keywords.size(), papersPerKeyword);

        int yrFrom = yearFrom != null ? yearFrom : 1900;
        int yrTo = yearTo != null ? yearTo : Year.now().getValue();
        int perPage = Math.min(100, Math.max(10, papersPerKeyword));
        int perKeyword = Math.max(1, papersPerKeyword);

        ApiSource source = getOrCreateArxivSource();
        int totalFetched = 0, totalInserted = 0;
        java.util.Map<String, java.util.Map<String, Integer>> keywordStats = new LinkedHashMap<>();

        if (bulkSyncProgressTracker.getProgress(taskId) == null) {
            bulkSyncProgressTracker.createTask(taskId, keywords.size());
        }

        for (String keyword : keywords) {
            int kwScanned = 0, kwInserted = 0, pageCount = 0;
            int skippedByYear = 0, skippedByDuplicate = 0;
            int start = 0;
            boolean hasMore = true;

            while (hasMore && kwInserted < perKeyword && pageCount < MAX_BULK_PAGES_PER_KEYWORD) {
                try {
                    // arXiv uses + as AND/TO operator — cannot use UriComponentsBuilder
                    String searchQuery = "all:" + keyword.replace(" ", "+")
                            + "+AND+submittedDate:[" + yrFrom + "01010000+TO+" + yrTo + "12312359]";
                    String url = source.getBaseUrl() + "/query?search_query=" + searchQuery
                            + "&start=" + start + "&max_results=" + perPage;
                    log.debug("arXiv bulk [{}] '{}' page {}: {}", taskId, keyword, pageCount + 1, url);

                    rateLimitArxiv();
                    String xmlResponse = fetchArxivWithRetry(url, keyword);
                    pageCount++;

                    if (xmlResponse == null || xmlResponse.isBlank()) {
                        hasMore = false;
                        break;
                    }

                    java.util.List<ParsedPaper> parsedPapers = parseArxivXml(xmlResponse);

                    // Extract totalResults from XML for pagination control
                    int totalResults = extractArxivTotalResults(xmlResponse);

                    if (parsedPapers.isEmpty()) {
                        hasMore = false;
                        break;
                    }

                    // Immediate UI update
                    bulkSyncProgressTracker.updatePageProgress(taskId, keyword,
                            kwScanned + parsedPapers.size(), kwInserted,
                            totalFetched + kwScanned + parsedPapers.size(),
                            totalInserted + kwInserted);

                    for (ParsedPaper pp : parsedPapers) {
                        if (kwInserted >= perKeyword) break;
                        kwScanned++;

                        if (pp.pubYear() != null && (pp.pubYear() < yrFrom || pp.pubYear() > yrTo)) {
                            skippedByYear++;
                            continue;
                        }

                        if (isDuplicatePaper(pp.doi(), pp.title(), pp.pubYear())) {
                            skippedByDuplicate++;
                            continue;
                        }

                        ResearchPaper paper = ResearchPaper.builder()
                                .source(source).title(pp.title())
                                .abstractText(pp.abstractText()).doi(pp.doi())
                                .pubYear(pp.pubYear()).citationCount(0).isOpenAccess(true)
                                .pdfUrl(pp.pdfUrl()).build();
                        setPublicationDate(paper, pp.pubDate());

                        ResearchPaper savedPaper = researchPaperRepository.save(paper);
                        kwInserted++;

                        java.util.List<String> kws = extractKeywordsFromTitle(pp.title());
                        savePaperToNeo4j(savedPaper, kws, keyword);
                        notificationTriggerService.notifyNewPaper(savedPaper);

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

                    // arXiv pagination: check totalResults
                    start += perPage;
                    if (start >= totalResults) {
                        hasMore = false;
                    }

                    if (pageCount % 5 == 0) {
                        log.info("arXiv bulk [{}] '{}': page {}, inserted {} (scanned: {}, totalResults: {})",
                                taskId, keyword, pageCount, kwInserted, kwScanned, totalResults);
                    }

                    bulkSyncProgressTracker.updatePageProgress(taskId, keyword, kwScanned, kwInserted,
                            totalFetched + kwScanned, totalInserted + kwInserted);
                } catch (Exception e) {
                    String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    log.warn("arXiv bulk [{}] page failed for '{}': {}", taskId, keyword, errorMsg);
                    bulkSyncProgressTracker.addKeywordError(taskId, keyword, errorMsg);
                    hasMore = false;
                }
            }

            totalFetched += kwScanned;
            totalInserted += kwInserted;
            keywordStats.put(keyword, java.util.Map.of(
                    "scanned", kwScanned, "inserted", kwInserted,
                    "skippedByYear", skippedByYear, "skippedByDuplicate", skippedByDuplicate));
            bulkSyncProgressTracker.updateKeywordProgress(taskId, keyword, kwScanned, kwInserted,
                    totalFetched, totalInserted);

            log.info("arXiv bulk [{}]: '{}' → scanned {}, inserted {} (pages: {}, {}%)",
                    taskId, keyword, kwScanned, kwInserted, pageCount,
                    (keywordStats.size() * 100) / keywords.size());
        }

        log.info("arXiv BULK SYNC [{}] DONE: {} keywords, {} total fetched, {} total inserted",
                taskId, keywords.size(), totalFetched, totalInserted);

        java.util.Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalKeywords", keywords.size());
        result.put("totalFetched", totalFetched);
        result.put("totalInserted", totalInserted);
        result.put("yearRange", yrFrom + "-" + yrTo);
        result.put("keywordStats", keywordStats);
        self.refreshStatsCache();
        return result;
    }

    @Override
    @Async("taskExecutor")
    public void bulkSyncFromArxivAsync(String taskId, java.util.List<String> keywords,
                                        int papersPerKeyword, Integer yearFrom, Integer yearTo) {
        try {
            java.util.Map<String, Object> result = bulkSyncFromArxiv(
                    taskId, keywords, papersPerKeyword, yearFrom, yearTo);
            bulkSyncProgressTracker.markCompleted(taskId, result);
            log.info("arXiv async bulk sync [{}] completed: {} papers", taskId,
                    result.getOrDefault("totalInserted", 0));
        } catch (Exception e) {
            log.error("arXiv async bulk sync [{}] failed: {}", taskId, e.getMessage(), e);
            bulkSyncProgressTracker.markFailed(taskId, e.getMessage());
        }
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
    public DatabaseStatsResponse getDatabaseStats() {
        long now = System.currentTimeMillis();
        // Return cached data if fresh (1 hour TTL)
        if (cachedStats != null && now < cachedStats.expiryTime) {
            return cachedStats.data;
        }

        // If we have stale cached data, return it + refresh async
        if (cachedStats != null) {
            self.refreshStatsCache();
            return cachedStats.data;
        }

        // No cache at all — try sync with timeout, fallback to empty
        try {
            var future = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                DatabaseStatsResponse.Neo4jStats neo4jStats = getNeo4jStats();
                return self.buildStatsFromJpa(neo4jStats);
            });
            DatabaseStatsResponse stats = future.get(30, java.util.concurrent.TimeUnit.SECONDS);
            cachedStats = new StatsCacheEntry(stats);
            log.info("Stats cache built: papers={}, authors={}, keywords={}",
                    stats.getPapers().getTotal(), stats.getAuthors().getTotal(), stats.getKeywords().getTotal());
            return stats;
        } catch (Exception e) {
            log.warn("Stats refresh timed out (30s) — returning empty. Will retry on next call.");
            self.refreshStatsCache(); // keep trying in background
            return buildEmptyStats();
        }
    }

    /**
     * Pre-compute stats asynchronously and store in cache.
     * Called after every sync and on cache miss to keep stats always warm.
     */
    @Async("taskExecutor")
    public void refreshStatsCache() {
        log.info("Refreshing stats cache in background...");
        long startTime = System.currentTimeMillis();
        try {
            DatabaseStatsResponse.Neo4jStats neo4jStats = getNeo4jStats();
            DatabaseStatsResponse stats = self.buildStatsFromJpa(neo4jStats);
            cachedStats = new StatsCacheEntry(stats);
            long elapsed = System.currentTimeMillis() - startTime;
            log.info("Stats cache refreshed in {}ms: papers={}, authors={}, keywords={}, neo4j=[papers={}, kw={}, rels={}]",
                    elapsed,
                    stats.getPapers().getTotal(), stats.getAuthors().getTotal(), stats.getKeywords().getTotal(),
                    neo4jStats.getPaperNodes(), neo4jStats.getKeywordNodes(), neo4jStats.getRelationships());
        } catch (Exception e) {
            log.warn("Stats cache refresh failed: {}", e.getMessage());
        }
    }

    /**
     * Lightweight empty stats response returned immediately on cache miss.
     * Client can retry in a few seconds after async refresh completes.
     */
    private DatabaseStatsResponse buildEmptyStats() {
        return DatabaseStatsResponse.builder()
                .papers(DatabaseStatsResponse.PaperStats.builder()
                        .total(0L).bySource(Map.of()).byYear(Map.of())
                        .openAccess(0L).hasPdfUrl(0L).build())
                .authors(DatabaseStatsResponse.AuthorStats.builder().total(0L).build())
                .keywords(DatabaseStatsResponse.KeywordStats.builder().total(0L).build())
                .journals(DatabaseStatsResponse.CountStat.builder().total(0L).build())
                .researchFields(DatabaseStatsResponse.CountStat.builder().total(0L).build())
                .researchTopics(DatabaseStatsResponse.TopicStats.builder().total(0L).trending(0L).build())
                .neo4j(DatabaseStatsResponse.Neo4jStats.builder()
                        .paperNodes(0L).keywordNodes(0L).relationships(0L).build())
                .syncLogs(DatabaseStatsResponse.SyncStats.builder().total(0L).lastSync(null).build())
                .build();
    }

    @Transactional(readOnly = true)
    public DatabaseStatsResponse buildStatsFromJpa(DatabaseStatsResponse.Neo4jStats neo4jStats) {
        // ── Single native query for all paper/count stats (avoids 11 round-trips) ──
        @SuppressWarnings("unchecked")
        List<Object[]> megaRow = entityManager.createNativeQuery(
            "SELECT "
          + "  (SELECT COUNT(*) FROM RESEARCH_PAPER), "
          + "  (SELECT COUNT(*) FROM RESEARCH_PAPER WHERE IsOpenAccess = 1), "
          + "  (SELECT COUNT(*) FROM RESEARCH_PAPER WHERE PdfUrl IS NOT NULL AND PdfUrl <> ''), "
          + "  (SELECT COUNT(*) FROM AUTHOR), "
          + "  (SELECT COUNT(*) FROM KEYWORD), "
          + "  (SELECT COUNT(*) FROM JOURNAL), "
          + "  (SELECT COUNT(*) FROM RESEARCH_FIELD), "
          + "  (SELECT COUNT(*) FROM RESEARCH_TOPIC), "
          + "  (SELECT COUNT(*) FROM RESEARCH_TOPIC WHERE IsTrending = 1), "
          + "  (SELECT COUNT(*) FROM SYNC_LOG), "
          + "  (SELECT MAX(CompletedAt) FROM SYNC_LOG WHERE CompletedAt IS NOT NULL)"
        ).getResultList();

        Object[] r = megaRow.get(0);
        long totalPapers   = ((Number) r[0]).longValue();
        long openAccess    = ((Number) r[1]).longValue();
        long hasPdf        = ((Number) r[2]).longValue();
        long totalAuthors  = ((Number) r[3]).longValue();
        long totalKeywords = ((Number) r[4]).longValue();
        long totalJournals = ((Number) r[5]).longValue();
        long totalFields   = ((Number) r[6]).longValue();
        long totalTopics   = ((Number) r[7]).longValue();
        long trending      = ((Number) r[8]).longValue();
        long totalSyncLogs = ((Number) r[9]).longValue();
        String lastSync    = r[10] != null ? r[10].toString() : null;

        // Papers by source (still needs GROUP BY — separate query)
        @SuppressWarnings("unchecked")
        List<Object[]> bySourceRows = entityManager.createNativeQuery(
            "SELECT s.SourceName, COUNT(p.PaperID) FROM RESEARCH_PAPER p "
          + "JOIN API_SOURCE s ON p.SourceID = s.SourceID GROUP BY s.SourceName"
        ).getResultList();
        Map<String, Long> bySource = new LinkedHashMap<>();
        for (Object[] row : bySourceRows) {
            bySource.put((String) row[0], ((Number) row[1]).longValue());
        }

        // Papers by year
        @SuppressWarnings("unchecked")
        List<Object[]> byYearRows = entityManager.createNativeQuery(
            "SELECT PubYear, COUNT(PaperID) FROM RESEARCH_PAPER WHERE PubYear IS NOT NULL "
          + "GROUP BY PubYear ORDER BY PubYear DESC"
        ).getResultList();
        Map<Integer, Long> byYear = new LinkedHashMap<>();
        for (Object[] row : byYearRows) {
            byYear.put(((Number) row[0]).intValue(), ((Number) row[1]).longValue());
        }

        DatabaseStatsResponse stats = DatabaseStatsResponse.builder()
                .papers(DatabaseStatsResponse.PaperStats.builder()
                        .total(totalPapers).bySource(bySource).byYear(byYear)
                        .openAccess(openAccess).hasPdfUrl(hasPdf).build())
                .authors(DatabaseStatsResponse.AuthorStats.builder()
                        .total(totalAuthors).build())
                .keywords(DatabaseStatsResponse.KeywordStats.builder()
                        .total(totalKeywords).build())
                .journals(DatabaseStatsResponse.CountStat.builder().total(totalJournals).build())
                .researchFields(DatabaseStatsResponse.CountStat.builder().total(totalFields).build())
                .researchTopics(DatabaseStatsResponse.TopicStats.builder()
                        .total(totalTopics).trending(trending).build())
                .neo4j(neo4jStats)
                .syncLogs(DatabaseStatsResponse.SyncStats.builder()
                        .total(totalSyncLogs).lastSync(lastSync).build())
                .build();

        return stats;
    }

    // ── Stats cache (1 hour TTL) ──
    private static final long CACHE_TTL_MS = 60 * 60 * 1000;
    private static class StatsCacheEntry {
        final DatabaseStatsResponse data;
        final long expiryTime;
        StatsCacheEntry(DatabaseStatsResponse data) {
            this.data = data;
            this.expiryTime = System.currentTimeMillis() + CACHE_TTL_MS;
        }
    }
    private StatsCacheEntry cachedStats;

    private DatabaseStatsResponse.Neo4jStats getNeo4jStats() {
        // Run Neo4j query in a dedicated thread with a hard 8s timeout.
        // Uses its own single-thread executor — never blocks the common ForkJoinPool.
        java.util.concurrent.ExecutorService neo4jExecutor = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "neo4j-stats-ds");
            t.setDaemon(true);
            return t;
        });
        try {
            var future = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                Map<String, Long> stats = graphService.getStats();
                return DatabaseStatsResponse.Neo4jStats.builder()
                        .paperNodes(stats.getOrDefault("paperNodes", 0L))
                        .keywordNodes(stats.getOrDefault("keywordNodes", 0L))
                        .relationships(stats.getOrDefault("relationships", 0L))
                        .build();
            }, neo4jExecutor);
            return future.get(8, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Neo4j stats timed out or failed after 8s — returning zeros: {}", e.getMessage());
            return DatabaseStatsResponse.Neo4jStats.builder()
                    .paperNodes(0L).keywordNodes(0L).relationships(0L).build();
        } finally {
            neo4jExecutor.shutdownNow();
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Re-extract keywords for all existing papers
    // ═══════════════════════════════════════════════════════════

    @Override
    @Transactional
    public java.util.Map<String, Object> reExtractKeywords() {
        log.info("Starting keyword re-extraction for all papers...");
        long totalPapers = researchPaperRepository.count();
        long processedCount = 0;
        long extractedCount = 0;

        int pageSize = 50;
        int page = 0;
        boolean hasMore = true;

        while (hasMore) {
            var paperPage = researchPaperRepository.findAll(
                    org.springframework.data.domain.PageRequest.of(page, pageSize));
            var papers = paperPage.getContent();

            if (papers.isEmpty()) {
                hasMore = false;
                break;
            }

            for (ResearchPaper paper : papers) {
                try {
                    // Skip papers that already have non-synthetic keywords from OpenAlex
                    boolean hasOpenAlexKeywords = paper.getKeywords() != null &&
                            paper.getKeywords().stream().anyMatch(pk ->
                                    pk.getRelevanceScore() != null && pk.getRelevanceScore() != 1.0);
                    if (hasOpenAlexKeywords) {
                        processedCount++;
                        continue;
                    }

                    List<String> extracted = keywordExtractionService.extract(
                            paper.getTitle(), paper.getAbstractText(), 5);
                    if (!extracted.isEmpty()) {
                        saveExtractedKeywords(paper, extracted);
                        // Skip Neo4j — already has keywords from original sync
                        extractedCount++;
                    }
                } catch (Exception e) {
                    log.warn("Keyword extraction failed for paper {}: {}", paper.getPaperId(), e.getMessage());
                }
                processedCount++;
            }

            page++;
            if (page >= paperPage.getTotalPages()) {
                hasMore = false;
            }

            if (processedCount % 100 == 0) {
                log.info("Keyword re-extraction progress: {}/{} papers", processedCount, totalPapers);
            }
        }

        log.info("Keyword re-extraction done: {} processed, {} papers got new keywords", processedCount, extractedCount);

        return java.util.Map.of(
                "totalPapers", totalPapers,
                "processed", processedCount,
                "extracted", extractedCount
        );
    }

    @Override
    public org.springframework.data.domain.Page<ResearchPaper> getRecentSyncedPapers(int hours, int page, int size) {
        LocalDateTime since = LocalDateTime.now().minusHours(Math.max(1, Math.min(hours, 720))); // clamp 1-720 hours
        return researchPaperRepository.findRecentPapers(
                since, org.springframework.data.domain.PageRequest.of(page, size));
    }

    // ═══════════════════════════════════════════════════════════
    //  Bulk sync (OpenAlex pagination)
    // ═══════════════════════════════════════════════════════════

    @Override
    public Map<String, Object> bulkSyncFromOpenAlex(List<String> keywords, int papersPerKeyword, Integer yearFrom, Integer yearTo) {
        return bulkSyncFromOpenAlex(keywords, papersPerKeyword, yearFrom, yearTo, null, null);
    }

    @Override
    public Map<String, Object> bulkSyncFromOpenAlex(List<String> keywords, int papersPerKeyword, Integer yearFrom, Integer yearTo, String mailto) {
        return bulkSyncFromOpenAlex(keywords, papersPerKeyword, yearFrom, yearTo, mailto, null);
    }

    public Map<String, Object> bulkSyncFromOpenAlex(List<String> keywords, int papersPerKeyword, Integer yearFrom, Integer yearTo, String mailto, String apiKey) {
        log.info("Starting BULK sync: {} keywords, {} papers each", keywords.size(), papersPerKeyword);

        int yrFrom = yearFrom != null ? yearFrom : 1900;
        int yrTo = yearTo != null ? yearTo : Year.now().getValue();
        int perPage = Math.min(200, Math.max(10, papersPerKeyword));
        int perKeyword = Math.max(1, papersPerKeyword);

        ApiSource source = getOrCreateOpenAlexSource();
        LocalDate today = LocalDate.now();
        int totalFetched = 0, totalInserted = 0;
        Map<String, Map<String, Integer>> keywordStats = new LinkedHashMap<>();

        for (String keyword : keywords) {
            int kwScanned = 0, kwInserted = 0, pageCount = 0;
            int skippedByYear = 0, skippedByDuplicate = 0;
            String nextCursor = "*";
            boolean hasMore = true;

            // Stop when: (1) enough papers inserted, (2) no more pages, or (3) safety cap reached
            while (hasMore && kwInserted < perKeyword && pageCount < MAX_BULK_PAGES_PER_KEYWORD) {
                try {
                    String url = withMailto(
                            UriComponentsBuilder
                                    .fromHttpUrl(source.getBaseUrl() + "/works")
                                    .queryParam("search", normalizeOpenAlexSearchQuery(keyword))
                                    .queryParam("filter", "from_publication_date:" + yrFrom + "-01-01,to_publication_date:" + today)
                                    .queryParam("sort", "publication_date:desc")
                                    .queryParam("per-page", perPage)
                                    .queryParam("cursor", nextCursor)
                                    .queryParam("select", "id,doi,title,display_name,publication_year,publication_date,cited_by_count,abstract_inverted_index,open_access,primary_location,best_oa_location,topics,keywords,authorships"),
                            mailto, apiKey
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

                        String doi = normalizeDoi(work.getDoi());
                        String title = trimToLength(resolveTitle(work), 1000);
                        if (isDuplicatePaper(doi, title, work.getPublicationYear())) {
                            skippedByDuplicate++;
                            continue;
                        }

                        ResearchField field = resolveResearchField(work);
                        ResearchPaper newPaper = ResearchPaper.builder()
                                .paperId(generatePaperIdFromOpenAlexWork(work))
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
                                .type(work.getType())
                                .openAlexWorkId(work.getId())
                                .build();
                        setPublicationDate(newPaper, work.getPublicationDate());

                        ResearchPaper savedPaper = researchPaperRepository.save(newPaper);
                        kwInserted++;

                        List<String> kws = saveOpenAlexKeywords(savedPaper, work, keyword);
                        savePaperToNeo4j(savedPaper, kws, keyword);

                        // Trigger notification for users following related journal/keyword
                        notificationTriggerService.notifyNewPaper(savedPaper);

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

                    // Log progress every 5 pages
                    if (pageCount % 5 == 0) {
                        log.info("Bulk sync '{}': page {}, inserted {} so far (scanned: {})", keyword, pageCount, kwInserted, kwScanned);
                    }

                    rateLimitOpenAlex(); // Rate limit
                } catch (Exception e) {
                    log.warn("Bulk sync page failed for '{}': {}", keyword, e.getMessage());
                    hasMore = false;
                }
            }

            totalFetched += kwScanned;
            totalInserted += kwInserted;
            Map<String, Integer> kwStats = new LinkedHashMap<>();
            kwStats.put("scanned", kwScanned);
            kwStats.put("inserted", kwInserted);
            kwStats.put("skippedByYear", skippedByYear);
            kwStats.put("skippedByRelevance", 0); // relevance check removed — OpenAlex already filters
            kwStats.put("skippedByDuplicate", skippedByDuplicate);
            keywordStats.put(keyword, kwStats);
            log.info("Bulk sync: '{}' → scanned {}, inserted {} (pages: {}, skipped: year={} dup={})",
                    keyword, kwScanned, kwInserted, pageCount, skippedByYear, skippedByDuplicate);
        }

        log.info("BULK SYNC DONE: {} keywords, {} total fetched, {} total inserted",
                keywords.size(), totalFetched, totalInserted);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalKeywords", keywords.size());
        result.put("totalFetched", totalFetched);
        result.put("totalInserted", totalInserted);
        result.put("yearRange", yrFrom + "-" + yrTo);
        result.put("keywordStats", keywordStats);

        // Pre-compute fresh stats in background so GET /stats is always instant
        self.refreshStatsCache();

        return result;
    }

    @Override
    public Map<String, Object> bulkSyncFromOpenAlex(String taskId, List<String> keywords,
                                                     int papersPerKeyword, Integer yearFrom, Integer yearTo,
                                                     String mailto, String apiKey) {
        log.info("Starting BULK sync [{}]: {} keywords, {} papers each", taskId, keywords.size(), papersPerKeyword);

        int yrFrom = yearFrom != null ? yearFrom : 1900;
        int yrTo = yearTo != null ? yearTo : Year.now().getValue();
        int perPage = Math.min(200, Math.max(10, papersPerKeyword));
        int perKeyword = Math.max(1, papersPerKeyword);

        ApiSource source = getOrCreateOpenAlexSource();
        LocalDate today = LocalDate.now();
        int totalFetched = 0, totalInserted = 0;
        Map<String, Map<String, Integer>> keywordStats = new LinkedHashMap<>();

        // Ensure task is registered
        if (bulkSyncProgressTracker.getProgress(taskId) == null) {
            bulkSyncProgressTracker.createTask(taskId, keywords.size());
        }

        for (String keyword : keywords) {
            int kwScanned = 0, kwInserted = 0, pageCount = 0;
            int skippedByYear = 0, skippedByDuplicate = 0;
            String nextCursor = "*";
            boolean hasMore = true;

            while (hasMore && kwInserted < perKeyword && pageCount < MAX_BULK_PAGES_PER_KEYWORD) {
                try {
                    String url = withMailto(
                            UriComponentsBuilder
                                    .fromHttpUrl(source.getBaseUrl() + "/works")
                                    .queryParam("search", normalizeOpenAlexSearchQuery(keyword))
                                    .queryParam("filter", "from_publication_date:" + yrFrom + "-01-01,to_publication_date:" + today)
                                    .queryParam("sort", "publication_date:desc")
                                    .queryParam("per-page", perPage)
                                    .queryParam("cursor", nextCursor)
                                    .queryParam("select", "id,doi,title,display_name,publication_year,publication_date,cited_by_count,abstract_inverted_index,open_access,primary_location,best_oa_location,topics,keywords,authorships"),
                            mailto, apiKey
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
                        String doi = normalizeDoi(work.getDoi());
                        String title = trimToLength(resolveTitle(work), 1000);
                        if (isDuplicatePaper(doi, title, work.getPublicationYear())) {
                            skippedByDuplicate++;
                            continue;
                        }

                        ResearchField field = resolveResearchField(work);
                        ResearchPaper newPaper = ResearchPaper.builder()
                                .paperId(generatePaperIdFromOpenAlexWork(work))
                                .source(source).title(title).abstractText(abstractText).doi(doi)
                                .journal(resolveJournal(work, source, field)).field(field)
                                .pubYear(work.getPublicationYear())
                                .citationCount(work.getCitedByCount() != null ? work.getCitedByCount() : 0)
                                .isOpenAccess(work.getOpenAccess() != null && Boolean.TRUE.equals(work.getOpenAccess().getIsOa()))
                                .pdfUrl(resolvePdfUrl(work)).build();
                        setPublicationDate(newPaper, work.getPublicationDate());

                        ResearchPaper savedPaper = researchPaperRepository.save(newPaper);
                        kwInserted++;

                        List<String> kws = saveOpenAlexKeywords(savedPaper, work, keyword);
                        savePaperToNeo4j(savedPaper, kws, keyword);
                        notificationTriggerService.notifyNewPaper(savedPaper);

                        if (work.getAuthorships() != null) {
                            int order = 1;
                            for (OpenAlexResponseDTO.Authorship authorship : work.getAuthorships()) {
                                if (order > MAX_AUTHORS_PER_PAPER) break;
                                Author author = getOrCreateOpenAlexAuthor(authorship, source);
                                savePaperAuthor(savedPaper, author, order++);
                            }
                        }
                    }

                    if (response.getMeta() != null && response.getMeta().getNextCursor() != null) {
                        nextCursor = response.getMeta().getNextCursor();
                    } else {
                        hasMore = false;
                    }

                    if (pageCount % 5 == 0) {
                        log.info("Bulk sync [{}] '{}': page {}, inserted {} (scanned: {})",
                                taskId, keyword, pageCount, kwInserted, kwScanned);
                    }

                    rateLimitOpenAlex();
                } catch (Exception e) {
                    String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    log.warn("Bulk sync [{}] page failed for '{}': {}", taskId, keyword, errorMsg);
                    bulkSyncProgressTracker.addKeywordError(taskId, keyword, errorMsg);
                    hasMore = false;
                }
            }

            totalFetched += kwScanned;
            totalInserted += kwInserted;
            keywordStats.put(keyword, Map.of("scanned", kwScanned, "inserted", kwInserted,
                    "skippedByYear", skippedByYear, "skippedByDuplicate", skippedByDuplicate));

            // Update progress after each keyword
            bulkSyncProgressTracker.updateKeywordProgress(taskId, keyword, kwScanned, kwInserted, totalFetched, totalInserted);

            log.info("Bulk sync [{}]: '{}' → scanned {}, inserted {} (pages: {}, {}%)",
                    taskId, keyword, kwScanned, kwInserted, pageCount,
                    (keywordStats.size() * 100) / keywords.size());
        }

        log.info("BULK SYNC [{}] DONE: {} keywords, {} total fetched, {} total inserted",
                taskId, keywords.size(), totalFetched, totalInserted);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalKeywords", keywords.size());
        result.put("totalFetched", totalFetched);
        result.put("totalInserted", totalInserted);
        result.put("yearRange", yrFrom + "-" + yrTo);
        result.put("keywordStats", keywordStats);

        self.refreshStatsCache();
        return result;
    }

    @Override
    @Async("taskExecutor")
    public void bulkSyncFromOpenAlexAsync(String taskId, List<String> keywords, int papersPerKeyword,
                                          Integer yearFrom, Integer yearTo, String mailto, String apiKey) {
        try {
            Map<String, Object> result = bulkSyncFromOpenAlex(
                    taskId, keywords, papersPerKeyword, yearFrom, yearTo, mailto, apiKey);
            bulkSyncProgressTracker.markCompleted(taskId, result);
            log.info("OpenAlex async bulk sync [{}] completed: {} papers inserted across {} keywords",
                    taskId, result.getOrDefault("totalInserted", 0), keywords.size());
        } catch (Exception e) {
            log.error("OpenAlex async bulk sync [{}] failed: {}", taskId, e.getMessage(), e);
            bulkSyncProgressTracker.markFailed(taskId, e.getMessage());
        }
    }

    /**
     * Same logic as {@link #bulkSyncFromOpenAlex(List, int, Integer, Integer)} but updates
     * progress in {@link BulkSyncProgressTracker} after each keyword.
     */
    private Map<String, Object> bulkSyncFromOpenAlexWithProgress(String taskId, List<String> keywords,
                                                                  int papersPerKeyword, Integer yearFrom, Integer yearTo) {
        return bulkSyncFromOpenAlexWithProgress(taskId, keywords, papersPerKeyword, yearFrom, yearTo, openalexEmail);
    }

    private Map<String, Object> bulkSyncFromOpenAlexWithProgress(String taskId, List<String> keywords,
                                                                  int papersPerKeyword, Integer yearFrom, Integer yearTo, String mailto) {
        log.info("Starting BULK sync [{}]: {} keywords, {} papers each", taskId, keywords.size(), papersPerKeyword);

        int yrFrom = yearFrom != null ? yearFrom : 1900;
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
            int skippedByYear = 0, skippedByDuplicate = 0;
            String nextCursor = "*";
            boolean hasMore = true;

            // Stop when: (1) enough papers inserted, (2) no more pages, or (3) safety cap reached
            while (hasMore && kwInserted < perKeyword && pageCount < MAX_BULK_PAGES_PER_KEYWORD) {
                try {
                    String url = withMailto(
                            UriComponentsBuilder
                                    .fromHttpUrl(source.getBaseUrl() + "/works")
                                    .queryParam("search", normalizeOpenAlexSearchQuery(keyword))
                                    .queryParam("filter", "from_publication_date:" + yrFrom + "-01-01,to_publication_date:" + today)
                                    .queryParam("sort", "publication_date:desc")
                                    .queryParam("per-page", perPage)
                                    .queryParam("cursor", nextCursor)
                                    .queryParam("select", "id,doi,title,display_name,publication_year,publication_date,cited_by_count,abstract_inverted_index,open_access,primary_location,best_oa_location,topics,keywords,authorships"),
                            mailto
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

                        String doi = normalizeDoi(work.getDoi());
                        String title = trimToLength(resolveTitle(work), 1000);
                        if (isDuplicatePaper(doi, title, work.getPublicationYear())) {
                            skippedByDuplicate++;
                            continue;
                        }

                        ResearchField field = resolveResearchField(work);
                        ResearchPaper newPaper = ResearchPaper.builder()
                                .paperId(generatePaperIdFromOpenAlexWork(work))
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
                                .type(work.getType())
                                .openAlexWorkId(work.getId())
                                .build();
                        setPublicationDate(newPaper, work.getPublicationDate());

                        ResearchPaper savedPaper = researchPaperRepository.save(newPaper);
                        kwInserted++;

                        List<String> kws = saveOpenAlexKeywords(savedPaper, work, keyword);
                        savePaperToNeo4j(savedPaper, kws, keyword);

                        // Trigger notification for users following related journal/keyword
                        notificationTriggerService.notifyNewPaper(savedPaper);

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

                    // Log progress every 5 pages
                    if (pageCount % 5 == 0) {
                        log.info("Bulk sync '{}': page {}, inserted {} so far (scanned: {})", keyword, pageCount, kwInserted, kwScanned);
                    }

                    rateLimitOpenAlex(); // Rate limit
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

            log.info("Bulk sync [{}]: '{}' → scanned {}, inserted {} (pages: {}, {}%, skipped: year={} dup={})",
                    taskId, keyword, kwScanned, kwInserted, pageCount,
                    (keywordStats.size() * 100) / keywords.size(),
                    skippedByYear, skippedByDuplicate);
        }

        log.info("BULK SYNC [{}] DONE: {} keywords, {} total fetched, {} total inserted",
                taskId, keywords.size(), totalFetched, totalInserted);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalKeywords", keywords.size());
        result.put("totalFetched", totalFetched);
        result.put("totalInserted", totalInserted);
        result.put("yearRange", yrFrom + "-" + yrTo);
        result.put("keywordStats", keywordStats);

        // Pre-compute fresh stats in background so GET /stats is always instant
        self.refreshStatsCache();

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

    private ApiSource getOrCreateSemanticScholarSource() {
        return apiSourceRepository.findBySourceName("semantic_scholar")
                .map(s -> {
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
                .orElseGet(() -> {
                    Author newAuthor = authorRepository.saveAndFlush(Author.builder()
                            .source(source)
                            .externalAuthorId(authorDTO.getAuthorId())
                            .fullName(fullName)
                            .build());
                    enqueueAuthorMetricsFetch(newAuthor);
                    return newAuthor;
                });
    }

    private Author getOrCreateOpenAlexAuthor(OpenAlexResponseDTO.Authorship authorship, ApiSource source) {
        String externalAuthorId = authorship.getAuthor() != null ? authorship.getAuthor().getId() : null;
        String fullName = resolveAuthorName(authorship);
        String affiliation = resolveAffiliation(authorship);
        String country = resolveCountry(authorship);

        if (externalAuthorId == null) {
            // Without an external ID, reuse by name first to avoid duplicate NULL IDs.
            return authorRepository.findByFullNameAndSource_SourceId(fullName, source.getSourceId())
                    .map(author -> fillMissingAuthorLocation(author, affiliation, country))
                    .orElseGet(() -> authorRepository.saveAndFlush(Author.builder()
                            .source(source)
                            .fullName(fullName)
                            .affiliation(affiliation)
                            .country(country)
                            .build()));
        }

        return authorRepository.findByExternalAuthorIdAndSource_SourceId(externalAuthorId, source.getSourceId())
                .map(author -> fillMissingAuthorLocation(author, affiliation, country))
                .orElseGet(() -> {
                    Author newAuthor = authorRepository.saveAndFlush(Author.builder()
                            .source(source)
                            .externalAuthorId(externalAuthorId)
                            .fullName(fullName)
                            .affiliation(affiliation)
                            .country(country)
                            .build());
                    // Fire-and-forget: fetch metrics from OpenAlex in background
                    enqueueAuthorMetricsFetch(newAuthor);
                    return newAuthor;
                });
    }

    /**
     * Async fetch of author hIndex/totalCitations/i10Index/worksCount from OpenAlex.
     * Only fires when the author has no metrics yet (hIndex == 0).
     */
    @org.springframework.scheduling.annotation.Async
    private void enqueueAuthorMetricsFetch(Author author) {
        if (author.getExternalAuthorId() == null) return;
        if (author.getHIndex() != null && author.getHIndex() > 0) return; // already have metrics

        try {
            Thread.sleep(500); // rate-limit: don't hammer OpenAlex
            String authorUrl = author.getExternalAuthorId();
            if (!authorUrl.startsWith("http")) {
                authorUrl = "https://api.openalex.org/authors/" + authorUrl;
            }

            var response = restTemplate.getForObject(authorUrl,
                    com.sra.journal_tracking.dto.author.OpenAlexAuthorResponseDTO.AuthorResult.class);

            if (response != null) {
                author.setHIndex(response.getHIndex() != null ? response.getHIndex() : 0);
                author.setTotalCitations(response.getCitedByCount() != null ? response.getCitedByCount() : 0);
                author.setI10Index(response.getI10Index() != null ? response.getI10Index() : 0);
                author.setWorksCount(response.getWorksCount() != null ? response.getWorksCount() : 0);
                authorRepository.save(author);
                log.debug("Synced author metrics for {}: hIndex={}, citations={}, i10={}, works={}",
                        author.getFullName(), author.getHIndex(), author.getTotalCitations(),
                        author.getI10Index(), author.getWorksCount());
            }
        } catch (Exception e) {
            log.warn("Failed to fetch author metrics for '{}': {}", author.getFullName(), e.getMessage());
        }
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

    private String resolveAffiliation(OpenAlexResponseDTO.Authorship authorship) {
        String rawAffiliation = resolveAffiliation(authorship.getRawAffiliationStrings());
        if (!isBlank(rawAffiliation)) {
            return rawAffiliation;
        }

        if (authorship.getInstitutions() == null) {
            return null;
        }

        for (OpenAlexResponseDTO.Institution institution : authorship.getInstitutions()) {
            if (institution == null || isBlank(institution.getDisplayName())) {
                continue;
            }
            return trimToLength(institution.getDisplayName(), 500);
        }
        return null;
    }

    private String resolveCountry(OpenAlexResponseDTO.Authorship authorship) {
        if (authorship.getCountries() != null && !authorship.getCountries().isEmpty()) {
            String country = normalizeCountry(authorship.getCountries().get(0));
            if (!isBlank(country)) {
                return trimToLength(country, 100);
            }
        }

        if (authorship.getInstitutions() != null) {
            for (OpenAlexResponseDTO.Institution institution : authorship.getInstitutions()) {
                if (institution == null || isBlank(institution.getCountryCode())) {
                    continue;
                }
                String country = normalizeCountry(institution.getCountryCode());
                if (!isBlank(country)) {
                    return trimToLength(country, 100);
                }
            }
        }

        return null;
    }

    private String normalizeCountry(String value) {
        if (isBlank(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() == 2) {
            return new Locale("", trimmed.toUpperCase(Locale.ROOT)).getDisplayCountry(Locale.ENGLISH);
        }
        return trimmed;
    }

    private Author fillMissingAuthorLocation(Author author, String affiliation, String country) {
        boolean changed = false;
        if (isBlank(author.getAffiliation()) && !isBlank(affiliation)) {
            author.setAffiliation(affiliation);
            changed = true;
        }
        if (isBlank(author.getCountry()) && !isBlank(country)) {
            author.setCountry(country);
            changed = true;
        }
        return changed ? authorRepository.save(author) : author;
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

    /**
     * Save extracted keywords to SQL (KEYWORD + PAPER_KEYWORD tables) for
     * sources that don't provide their own keywords (arXiv, CORE, Semantic Scholar).
     */
    private List<String> saveExtractedKeywords(ResearchPaper paper, List<String> keywordTexts) {
        if (keywordTexts == null || keywordTexts.isEmpty()) {
            return List.of();
        }

        List<String> savedTexts = new ArrayList<>();
        for (String rawText : keywordTexts) {
            if (savedTexts.size() >= MAX_KEYWORDS_PER_PAPER) break;
            if (isBlank(rawText)) continue;

            String keywordText = trimToLength(rawText.trim(), 300);
            String normalizedText = normalizeKeyword(keywordText);
            if (isBlank(normalizedText)) continue;

            Keyword keyword = keywordRepository.findByNormalizedText(normalizedText)
                    .orElseGet(() -> keywordRepository.save(Keyword.builder()
                            .field(paper.getField())
                            .keywordText(keywordText)
                            .normalizedText(normalizedText)
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
                        .relevanceScore(0.75) // extracted keyword, slightly below API-provided
                        .build());
            }

            savedTexts.add(keywordText);
        }

        return savedTexts;
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
        // API key (required since Feb 2026 — replaces the deprecated mailto polite pool)
        if (openalexApiKey != null && !openalexApiKey.isBlank()) {
            builder.queryParam("api_key", openalexApiKey);
        }
        // mailto kept for backward compatibility (no longer works for auth, but doesn't hurt)
        if (openalexEmail != null && !openalexEmail.isBlank()) {
            builder.queryParam("mailto", openalexEmail);
        }
        return builder;
    }

    /**
     * Add API key and optional mailto (for team members using their own quota).
     * API key is REQUIRED since Feb 2026 — the old mailto polite pool is deprecated.
     */
    private UriComponentsBuilder withMailto(UriComponentsBuilder builder, String mailto) {
        return withMailto(builder, mailto, null);
    }

    /**
     * Add API key and optional mailto with user-provided API key override.
     * Uses user's apiKey if provided, otherwise falls back to server-wide openalexApiKey.
     */
    private UriComponentsBuilder withMailto(UriComponentsBuilder builder, String mailto, String apiKey) {
        String effectiveKey = (apiKey != null && !apiKey.isBlank()) ? apiKey : openalexApiKey;
        if (effectiveKey != null && !effectiveKey.isBlank()) {
            builder.queryParam("api_key", effectiveKey);
        }
        if (mailto != null && !mailto.isBlank()) {
            builder.queryParam("mailto", mailto);
        }
        return builder;
    }

    /**
     * Dynamic rate limiter: sleeps only the remaining time needed to maintain
     * the minimum interval since the last request. No wasted sleep.
     */
    private void rateLimitCore() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastCoreRequestTime;
        long waitMs = CORE_MIN_INTERVAL_MS - elapsed;
        if (waitMs > 0) {
            try {
                Thread.sleep(waitMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        lastCoreRequestTime = System.currentTimeMillis();
    }

    private void rateLimitOpenAlex() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastOpenAlexRequestTime;
        long waitMs = OPENALEX_MIN_INTERVAL_MS - elapsed;
        if (waitMs > 0) {
            try {
                Thread.sleep(waitMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        lastOpenAlexRequestTime = System.currentTimeMillis();
    }

    private void rateLimitSemanticScholar() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastSemanticScholarRequestTime;
        long waitMs = SEMANTIC_SCHOLAR_MIN_INTERVAL_MS - elapsed;
        if (waitMs > 0) {
            try {
                Thread.sleep(waitMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        lastSemanticScholarRequestTime = System.currentTimeMillis();
    }

    private void rateLimitArxiv() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastArxivRequestTime;
        long waitMs = ARXIV_MIN_INTERVAL_MS - elapsed;
        if (waitMs > 0) {
            try {
                Thread.sleep(waitMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        lastArxivRequestTime = System.currentTimeMillis();
    }

    /**
     * Call OpenAlex API with retry on 429 rate limit.
     */
    @SuppressWarnings("unchecked")
    private <T> T fetchOpenAlexWithRetry(String url, Class<T> responseType, String context) {
        int maxRetries = 5;
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                return restTemplate.getForObject(url, responseType);
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : "";
                boolean isRateLimited = msg.contains("429") || msg.contains("Rate limit") || msg.contains("Too Many Requests");
                boolean isServerError = msg.contains("503") || msg.contains("Service Unavailable")
                        || msg.contains("Search temporarily unavailable");
                if ((isRateLimited || isServerError) && attempt < maxRetries - 1) {
                    long waitMs = isServerError
                            ? (attempt + 1) * 5000L   // 5s, 10s, 15s, 20s — server overload needs longer wait
                            : (attempt + 1) * 3000L;  // 3s, 6s, 9s, 12s — rate limit
                    log.warn("OpenAlex {} for '{}' (attempt {}/{}), waiting {}s...",
                            isServerError ? "503 overload" : "429 rate-limited",
                            context, attempt + 1, maxRetries, waitMs / 1000);
                    try { Thread.sleep(waitMs); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                } else {
                    throw new RuntimeException("OpenAlex API error for '" + context + "': " + msg, e);
                }
            }
        }
        return null;
    }

    /**
     * Call CORE API with retry on 429 rate limit.
     * CORE API uses Bearer token auth via HttpEntity, so we wrap exchange() instead of getForObject().
     */
    @SuppressWarnings("unchecked")
    private java.util.Map<String, Object> fetchCoreWithRetry(String url,
                                                              org.springframework.http.HttpEntity<String> entity,
                                                              String context) {
        int maxRetries = 5;
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                var response = restTemplate.exchange(url, org.springframework.http.HttpMethod.GET,
                        entity, java.util.Map.class);
                return response.getBody();
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : "";
                boolean isRateLimited = msg.contains("429") || msg.contains("Rate limit")
                        || msg.contains("Too Many Requests");
                boolean isServerError = msg.contains("503") || msg.contains("Service Unavailable")
                        || msg.contains("Server Error");
                boolean isCancelled = msg.contains("cancelled") || msg.contains("Cancel");
                // Treat cancellation as rate-limit (proxy/circuit-breaker killing the request)
                boolean retryable = isRateLimited || isServerError || isCancelled;
                if (retryable && attempt < maxRetries - 1) {
                    long waitMs = isServerError
                            ? (attempt + 1) * 5000L   // 5s, 10s, 15s, 20s
                            : (attempt + 1) * 4000L;  // 4s, 8s, 12s, 16s — CORE rate limit is stricter
                    log.warn("CORE {} for '{}' (attempt {}/{}), waiting {}s...",
                            isServerError ? "503" : isCancelled ? "connection-cancelled (likely rate-limited)" : "429",
                            context, attempt + 1, maxRetries, waitMs / 1000);
                    try {
                        Thread.sleep(waitMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    throw new RuntimeException("CORE API error for '" + context + "': " + msg, e);
                }
            }
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════════
    //  Semantic Scholar & arXiv retry helpers
    // ═══════════════════════════════════════════════════════════

    /**
     * Call Semantic Scholar API with retry on 429/503.
     */
    private SemanticScholarResponseDTO fetchSemanticScholarWithRetry(String url, String context,
                                                                       org.springframework.http.HttpEntity<String> entity) {
        int maxRetries = 5;
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                if (entity != null) {
                    // Authenticated request with x-api-key header
                    var response = restTemplate.exchange(url, org.springframework.http.HttpMethod.GET,
                            entity, SemanticScholarResponseDTO.class);
                    return response.getBody();
                }
                return restTemplate.getForObject(url, SemanticScholarResponseDTO.class);
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : "";
                boolean retryable = msg.contains("429") || msg.contains("503")
                        || msg.contains("Rate limit") || msg.contains("Too Many Requests");
                if (retryable && attempt < maxRetries - 1) {
                    long waitMs = (attempt + 1) * 5000L; // 5s, 10s, 15s, 20s
                    log.warn("Semantic Scholar rate-limited for '{}' (attempt {}/{}), waiting {}s...",
                            context, attempt + 1, maxRetries, waitMs / 1000);
                    try { Thread.sleep(waitMs); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                } else {
                    throw new RuntimeException("Semantic Scholar API error for '" + context + "': " + msg, e);
                }
            }
        }
        return null;
    }

    /**
     * Call arXiv API with retry. arXiv returns XML string.
     */
    private String fetchArxivWithRetry(String url, String context) {
        int maxRetries = 3; // arXiv is strict — fewer retries, longer waits
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                return restTemplate.getForObject(url, String.class);
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : "";
                boolean retryable = msg.contains("503") || msg.contains("429")
                        || msg.contains("Rate limit") || msg.contains("Too Many Requests");
                if (retryable && attempt < maxRetries - 1) {
                    long waitMs = (attempt + 1) * 10000L; // 10s, 20s — arXiv needs very long waits
                    log.warn("arXiv rate-limited for '{}' (attempt {}/{}), waiting {}s...",
                            context, attempt + 1, maxRetries, waitMs / 1000);
                    try { Thread.sleep(waitMs); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                } else {
                    throw new RuntimeException("arXiv API error for '" + context + "': " + msg, e);
                }
            }
        }
        return null;
    }

    /**
     * Extract totalResults from arXiv XML Atom feed for pagination control.
     */
    private int extractArxivTotalResults(String xml) {
        try {
            // <opensearch:totalResults>123</opensearch:totalResults>
            int startIdx = xml.indexOf("<opensearch:totalResults>");
            if (startIdx < 0) {
                startIdx = xml.indexOf("<totalResults>");
            }
            if (startIdx >= 0) {
                startIdx = xml.indexOf(">", startIdx) + 1;
                int endIdx = xml.indexOf("<", startIdx);
                if (endIdx > startIdx) {
                    return Integer.parseInt(xml.substring(startIdx, endIdx).trim());
                }
            }
        } catch (Exception e) {
            log.debug("Failed to extract arXiv totalResults: {}", e.getMessage());
        }
        return Integer.MAX_VALUE; // if we can't parse, assume more pages
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
        // ── Save search query to SQL Keyword table so exact-match queries work ──
        if (searchQuery != null && !searchQuery.isBlank()) {
            try {
                saveExtractedKeywords(paper, List.of(searchQuery.trim()));
            } catch (Exception e) {
                log.warn("SQL keyword save skipped for paper {}: {}", paper.getPaperId(), e.getMessage());
            }
        }

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

    /**
     * Extract keywords from paper title using {@link KeywordExtractionService}.
     * Delegates to the shared NLP-based extraction with empty abstract.
     */
    private List<String> extractKeywordsFromTitle(String title) {
        return keywordExtractionService.extract(title, "");
    }

    /**
     * Backfill author metrics (hIndex, totalCitations, i10Index, worksCount)
     * from OpenAlex for authors that have an externalAuthorId but hIndex=0.
     * Rate-limited: ~3 calls/sec to respect OpenAlex polite pool.
     */
    @Override
    public java.util.Map<String, Object> backfillAuthorMetrics(int limit) {
        var authors = authorRepository.findAll().stream()
                .filter(a -> a.getExternalAuthorId() != null && !a.getExternalAuthorId().isBlank())
                .filter(a -> a.getHIndex() == null || a.getHIndex() == 0)
                .collect(java.util.stream.Collectors.toList());

        if (limit > 0 && authors.size() > limit) {
            authors = authors.subList(0, limit);
        }

        int updated = 0, skipped = 0, errors = 0;
        log.info("Backfill author metrics: {} authors to process (limit={})", authors.size(), limit);

        for (Author author : authors) {
            try {
                String authorUrl = author.getExternalAuthorId();
                if (!authorUrl.startsWith("http")) {
                    authorUrl = "https://api.openalex.org/authors/" + authorUrl;
                }

                // Add mailto for polite pool
                var uriBuilder = UriComponentsBuilder.fromHttpUrl(authorUrl);
                if (openalexEmail != null && !openalexEmail.isBlank()) {
                    uriBuilder.queryParam("mailto", openalexEmail);
                }
                String url = uriBuilder.build(true).toUriString();

                // Fetch raw JSON first (more robust than direct deserialization)
                String rawJson = restTemplate.getForObject(url, String.class);
                if (rawJson == null || rawJson.isBlank()) {
                    skipped++;
                    continue;
                }

                var response = objectMapper.readValue(rawJson,
                        com.sra.journal_tracking.dto.author.OpenAlexAuthorResponseDTO.AuthorResult.class);

                if (response != null) {
                    author.setHIndex(response.getHIndex() != null ? response.getHIndex() : 0);
                    author.setTotalCitations(response.getCitedByCount() != null ? response.getCitedByCount() : 0);
                    author.setI10Index(response.getI10Index() != null ? response.getI10Index() : 0);
                    author.setWorksCount(response.getWorksCount() != null ? response.getWorksCount() : 0);
                    authorRepository.save(author);
                    updated++;
                    if (updated % 50 == 0) {
                        log.info("Backfill progress: {}/{} authors updated", updated, authors.size());
                    }
                } else {
                    skipped++;
                }

                Thread.sleep(350); // ~3 req/sec for polite pool
            } catch (Exception e) {
                errors++;
                String errMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                log.warn("Backfill failed for '{}' (id={}): {}",
                        author.getFullName(), author.getExternalAuthorId(), errMsg);
            }
        }

        log.info("Backfill author metrics done: updated={}, skipped={}, errors={}, total={}",
                updated, skipped, errors, authors.size());

        return java.util.Map.of(
                "totalProcessed", authors.size(),
                "updated", updated,
                "skipped", skipped,
                "errors", errors
        );
    }

}
