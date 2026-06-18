package com.sra.journal_tracking.service.impl;

import com.sra.journal_tracking.dto.sync.OpenAlexResponseDTO;
import com.sra.journal_tracking.dto.sync.SemanticScholarResponseDTO;
import com.sra.journal_tracking.entity.jpa.ApiSource;
import com.sra.journal_tracking.entity.jpa.Author;
import com.sra.journal_tracking.entity.jpa.AutoSyncKeyword;
import com.sra.journal_tracking.entity.jpa.PaperAuthor;
import com.sra.journal_tracking.entity.jpa.PaperAuthorId;
import com.sra.journal_tracking.entity.jpa.Keyword;
import com.sra.journal_tracking.entity.jpa.PaperKeyword;
import com.sra.journal_tracking.entity.jpa.PaperKeywordId;
import com.sra.journal_tracking.entity.jpa.ResearchPaper;
import com.sra.journal_tracking.entity.jpa.SyncLog;
import com.sra.journal_tracking.repository.jpa.ApiSourceRepository;
import com.sra.journal_tracking.repository.jpa.AuthorRepository;
import com.sra.journal_tracking.repository.jpa.AutoSyncKeywordRepository;
import com.sra.journal_tracking.repository.jpa.KeywordRepository;
import com.sra.journal_tracking.repository.jpa.PaperAuthorRepository;
import com.sra.journal_tracking.repository.jpa.PaperKeywordRepository;
import com.sra.journal_tracking.repository.jpa.ResearchPaperRepository;
import com.sra.journal_tracking.repository.jpa.SyncLogRepository;
import com.sra.journal_tracking.service.DataSyncService;
import com.sra.journal_tracking.service.GraphService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataSyncServiceImpl implements DataSyncService {

    // Common English stop words + short/meaningless words to filter out
    private static final Set<String> STOP_WORDS = Set.of(
        "the","a","an","is","are","was","were","be","been","being","have","has","had",
        "do","does","did","will","would","could","should","may","might","shall","can",
        "this","that","these","those","it","its","we","they","them","he","she","his",
        "her","their","our","my","your","in","on","at","to","for","of","from","with",
        "by","about","as","into","through","during","before","after","above","below",
        "between","under","and","but","or","nor","not","so","if","than","too","very",
        "just","also","now","then","here","there","when","where","why","how","all",
        "each","every","both","few","more","most","other","some","such","no","only",
        "own","same","up","out","new","over","use","used","using","make","made",
        "well","back","still","however","thus","although","because","since","while",
        "which","who","whom","whose","what","whether","yet","much","many","one","two"
    );

    private static final Pattern WORD_PATTERN = Pattern.compile("[^a-zA-Z]");
    private static final int MIN_KEYWORD_LENGTH = 3;

    private final ResearchPaperRepository researchPaperRepository;
    private final AuthorRepository authorRepository;
    private final PaperAuthorRepository paperAuthorRepository;
    private final KeywordRepository keywordRepository;
    private final PaperKeywordRepository paperKeywordRepository;
    private final ApiSourceRepository apiSourceRepository;
    private final SyncLogRepository syncLogRepository;
    private final GraphService graphService;
    private final RestTemplate restTemplate;
    private final AutoSyncKeywordRepository autoSyncKeywordRepository;

    @Override
    @Transactional
    public SyncLog syncFromSemanticScholar(String query, int limit) {
        log.info("Starting Semantic Scholar sync for query: {}", query);

        ApiSource source = apiSourceRepository.findBySourceName("semantic_scholar")
                .orElseThrow(() -> new RuntimeException("Semantic Scholar API Source not found in DB"));

        SyncLog syncLog = createRunningSyncLog(source, false);

        try {
            String fields = "paperId,title,abstract,year,publicationDate,isOpenAccess,citationCount,authors,externalIds";
            String url = String.format("%s/graph/v1/paper/search?query=%s&fields=%s&limit=%d",
                    source.getBaseUrl(), query, fields, limit);

            SemanticScholarResponseDTO response = restTemplate.getForObject(url, SemanticScholarResponseDTO.class);

            if (response != null && response.getData() != null) {
                int insertedCount = 0;
                syncLog.setPapersFetched(response.getData().size());

                for (SemanticScholarResponseDTO.SemanticScholarPaperDTO paperDTO : response.getData()) {
                    String doi = null;
                    if (paperDTO.getExternalIds() != null) {
                        doi = normalizeDoi(paperDTO.getExternalIds().getDOI());
                    }

                    if (doi != null && researchPaperRepository.findByDoi(doi).isPresent()) {
                        continue;
                    }

                    ResearchPaper newPaper = ResearchPaper.builder()
                            .source(source)
                            .title(paperDTO.getTitle() != null ? paperDTO.getTitle() : "Untitled")
                            .abstractText(paperDTO.getAbstractText())
                            .doi(doi)
                            .pubYear(paperDTO.getYear())
                            .citationCount(paperDTO.getCitationCount() != null ? paperDTO.getCitationCount() : 0)
                            .isOpenAccess(paperDTO.getIsOpenAccess() != null ? paperDTO.getIsOpenAccess() : false)
                            .build();

                    setPublicationDate(newPaper, paperDTO.getPublicationDate());

                    ResearchPaper savedPaper = researchPaperRepository.save(newPaper);
                    insertedCount++;

                    // Lưu vào Neo4j graph để tìm kiếm lần sau nhanh hơn
                    savePaperToNeo4j(savedPaper, List.of(query));

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
    @Transactional
    public SyncLog syncFromOpenAlex(String query, int limit) {
        log.info("Starting OpenAlex sync for query: {}", query);

        ApiSource source = getOrCreateOpenAlexSource();
        SyncLog syncLog = createRunningSyncLog(source, true);

        try {
            int perPage = Math.min(100, Math.max(1, limit));
            String url = UriComponentsBuilder
                    .fromHttpUrl(source.getBaseUrl() + "/works")
                    .queryParam("search", query)
                    .queryParam("per-page", perPage)
                    .queryParam("select", "id,doi,title,display_name,publication_year,publication_date,cited_by_count,abstract_inverted_index,open_access,authorships,concepts")
                    .build()
                    .toUriString();

            OpenAlexResponseDTO response = restTemplate.getForObject(url, OpenAlexResponseDTO.class);

            if (response != null && response.getResults() != null) {
                int insertedCount = 0;
                syncLog.setPapersFetched(response.getResults().size());

                for (OpenAlexResponseDTO.OpenAlexWorkDTO work : response.getResults()) {
                    String doi = normalizeDoi(work.getDoi());
                    if (doi != null && researchPaperRepository.findByDoi(doi).isPresent()) {
                        continue;
                    }

                    ResearchPaper newPaper = ResearchPaper.builder()
                            .source(source)
                            .title(resolveTitle(work))
                            .abstractText(rebuildAbstract(work.getAbstractInvertedIndex()))
                            .doi(doi)
                            .pubYear(work.getPublicationYear())
                            .citationCount(work.getCitedByCount() != null ? work.getCitedByCount() : 0)
                            .isOpenAccess(work.getOpenAccess() != null && Boolean.TRUE.equals(work.getOpenAccess().getIsOa()))
                            .build();

                    setPublicationDate(newPaper, work.getPublicationDate());

                    ResearchPaper savedPaper = researchPaperRepository.save(newPaper);
                    insertedCount++;

                    // Lưu concepts làm keywords vào SQL + Neo4j
                    List<String> keywordTexts = saveConceptsAsKeywords(savedPaper, work.getConcepts());
                    savePaperToNeo4j(savedPaper, keywordTexts.isEmpty() ? List.of(query) : keywordTexts);

                    if (work.getAuthorships() != null) {
                        int order = 1;
                        for (OpenAlexResponseDTO.Authorship authorship : work.getAuthorships()) {
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
    @Transactional
    public List<ResearchPaper> syncFromOpenAlexAndReturnPapers(String query, int limit) {
        log.info("Starting OpenAlex sync (return-papers mode) for query: {}", query);

        ApiSource source = getOrCreateOpenAlexSource();
        List<ResearchPaper> savedPapers = new ArrayList<>();

        try {
            int perPage = Math.min(100, Math.max(1, limit));
            String url = UriComponentsBuilder
                    .fromHttpUrl(source.getBaseUrl() + "/works")
                    .queryParam("search", query)
                    .queryParam("per-page", perPage)
                    .queryParam("select", "id,doi,title,display_name,publication_year,publication_date,cited_by_count,abstract_inverted_index,open_access,authorships,concepts")
                    .build()
                    .toUriString();

            OpenAlexResponseDTO response = restTemplate.getForObject(url, OpenAlexResponseDTO.class);

            if (response != null && response.getResults() != null) {
                for (OpenAlexResponseDTO.OpenAlexWorkDTO work : response.getResults()) {
                    String doi = normalizeDoi(work.getDoi());
                    if (doi != null && researchPaperRepository.findByDoi(doi).isPresent()) {
                        continue;
                    }

                    ResearchPaper newPaper = ResearchPaper.builder()
                            .source(source)
                            .title(resolveTitle(work))
                            .abstractText(rebuildAbstract(work.getAbstractInvertedIndex()))
                            .doi(doi)
                            .pubYear(work.getPublicationYear())
                            .citationCount(work.getCitedByCount() != null ? work.getCitedByCount() : 0)
                            .isOpenAccess(work.getOpenAccess() != null && Boolean.TRUE.equals(work.getOpenAccess().getIsOa()))
                            .build();

                    setPublicationDate(newPaper, work.getPublicationDate());

                    ResearchPaper savedPaper = researchPaperRepository.save(newPaper);
                    savedPapers.add(savedPaper);

                    // Save concepts as keywords to SQL + Neo4j
                    List<String> keywordTexts = saveConceptsAsKeywords(savedPaper, work.getConcepts());
                    savePaperToNeo4j(savedPaper, keywordTexts.isEmpty() ? List.of(query) : keywordTexts);

                    if (work.getAuthorships() != null) {
                        int order = 1;
                        for (OpenAlexResponseDTO.Authorship authorship : work.getAuthorships()) {
                            Author author = getOrCreateOpenAlexAuthor(authorship, source);
                            savePaperAuthor(savedPaper, author, order++);
                        }
                    }
                }

                log.info("OpenAlex sync (return-papers) completed. Saved {} papers for '{}'", savedPapers.size(), query);
            }
        } catch (Exception e) {
            log.error("OpenAlex sync (return-papers) failed for '{}': {}", query, e.getMessage());
        } finally {
            source.setLastSyncedAt(LocalDateTime.now());
            apiSourceRepository.save(source);
        }

        return savedPapers;
    }

    @Override
    @Transactional
    public int backfillKeywordsToNeo4j(int batchSize) {
        log.info("Starting keyword backfill (with text extraction fallback) to Neo4j (batch size: {})", batchSize);

        int totalSynced = 0;
        int totalExtracted = 0;
        int page = 0;
        org.springframework.data.domain.Page<ResearchPaper> paperPage;

        do {
            paperPage = researchPaperRepository.findAll(
                    org.springframework.data.domain.PageRequest.of(page, batchSize));
            List<ResearchPaper> papers = paperPage.getContent();

            for (ResearchPaper paper : papers) {
                // Force load keywords via repository
                List<ResearchPaper> detailed = researchPaperRepository.findByIdsWithDetails(
                        List.of(paper.getPaperId()));
                if (detailed.isEmpty()) continue;

                ResearchPaper detailedPaper = detailed.get(0);
                List<String> keywordTexts = new ArrayList<>();

                if (detailedPaper.getKeywords() != null && !detailedPaper.getKeywords().isEmpty()) {
                    // Case 1: Paper already has keywords in PAPER_KEYWORD table
                    for (PaperKeyword pk : detailedPaper.getKeywords()) {
                        String kwText = pk.getKeyword().getKeywordText();
                        if (kwText != null && !kwText.isBlank()) {
                            keywordTexts.add(kwText.trim());
                        }
                    }
                } else {
                    // Case 2: No keywords → extract from title + abstract
                    keywordTexts = extractAndSaveKeywords(detailedPaper);
                    if (!keywordTexts.isEmpty()) {
                        totalExtracted++;
                    }
                }

                if (!keywordTexts.isEmpty()) {
                    savePaperToNeo4j(detailedPaper, keywordTexts);
                    totalSynced++;
                }
            }

            page++;
            if (page % 5 == 0) {
                log.info("Backfill progress: page {}, synced {} papers, extracted keywords for {} papers",
                        page, totalSynced, totalExtracted);
            }
        } while (paperPage.hasNext());

        log.info("Keyword backfill completed. Synced: {} papers, Extracted keywords for: {} papers (no existing keywords)",
                totalSynced, totalExtracted);
        return totalSynced;
    }

    /**
     * Extract meaningful keywords from paper title and abstract text.
     * Filters: remove punctuation, stop words, short words, numbers.
     * Saves extracted keywords to SQL PAPER_KEYWORD table.
     */
    private List<String> extractAndSaveKeywords(ResearchPaper paper) {
        String title = paper.getTitle() != null ? paper.getTitle() : "";
        String abs = paper.getAbstractText() != null ? paper.getAbstractText() : "";
        String combined = title + " " + abs;

        // Extract unique meaningful words
        Set<String> keywords = Arrays.stream(combined.toLowerCase().split("\\s+"))
                .map(word -> WORD_PATTERN.matcher(word).replaceAll("").trim())
                .filter(word -> word.length() >= MIN_KEYWORD_LENGTH)
                .filter(word -> !STOP_WORDS.contains(word))
                .filter(word -> !word.matches("\\d+"))  // skip pure numbers
                .filter(word -> !word.matches("^[ivxlcdm]+$"))  // skip roman numerals
                .limit(20)  // max 20 keywords per paper
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (keywords.isEmpty()) {
            return List.of();
        }

        // Save to SQL PAPER_KEYWORD table
        List<String> savedTexts = new ArrayList<>();
        for (String kw : keywords) {
            String text = kw.substring(0, 1).toUpperCase() + kw.substring(1); // Capitalize
            String normalized = kw;

            try {
                Keyword keyword = keywordRepository.findByNormalizedText(normalized)
                        .orElseGet(() -> keywordRepository.save(Keyword.builder()
                                .keywordText(text)
                                .normalizedText(normalized)
                                .paperCount(0)
                                .build()));

                PaperKeywordId pkId = new PaperKeywordId(paper.getPaperId(), keyword.getKeywordId());
                if (paperKeywordRepository.findById(pkId).isEmpty()) {
                    paperKeywordRepository.save(PaperKeyword.builder()
                            .id(pkId)
                            .paper(paper)
                            .keyword(keyword)
                            .relevanceScore(0.5)  // default score for extracted keywords
                            .build());
                }

                savedTexts.add(text);
            } catch (Exception e) {
                log.debug("Skip keyword '{}' for paper {}: {}", kw, paper.getPaperId(), e.getMessage());
            }
        }

        log.debug("Extracted {} keywords from paper '{}'", savedTexts.size(),
                title.length() > 50 ? title.substring(0, 50) + "..." : title);
        return savedTexts;
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

    private Author getOrCreateSemanticScholarAuthor(
            SemanticScholarResponseDTO.SemanticScholarPaperDTO.AuthorDTO authorDTO,
            ApiSource source) {
        String fullName = authorDTO.getName() != null ? authorDTO.getName() : "Unknown Author";

        if (authorDTO.getAuthorId() == null) {
            // Không có external ID → tìm theo tên trước, dùng saveAndFlush để tránh duplicate NULL
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
            // Không có external ID → tìm theo tên trước, dùng saveAndFlush để tránh duplicate NULL
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
        PaperAuthor paperAuthor = PaperAuthor.builder()
                .id(new PaperAuthorId()) // @EmbeddedId cần được khởi tạo, @MapsId sẽ tự điền
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
     * Lưu concepts từ OpenAlex thành Keyword + PaperKeyword trong SQL.
     * Trả về danh sách keyword text để sync sang Neo4j.
     */
    private List<String> saveConceptsAsKeywords(ResearchPaper paper, List<OpenAlexResponseDTO.Concept> concepts) {
        if (concepts == null || concepts.isEmpty()) {
            return List.of();
        }

        List<String> keywordTexts = new ArrayList<>();

        for (OpenAlexResponseDTO.Concept concept : concepts) {
            if (concept.getDisplayName() == null || concept.getDisplayName().isBlank()) {
                continue;
            }

            String text = concept.getDisplayName().trim();
            String normalized = text.toLowerCase().trim();

            // Upsert keyword in SQL
            Keyword keyword = keywordRepository.findByNormalizedText(normalized)
                    .orElseGet(() -> keywordRepository.save(Keyword.builder()
                            .keywordText(text)
                            .normalizedText(normalized)
                            .paperCount(0)
                            .build()));

            // Create PaperKeyword association (skip if already exists)
            PaperKeywordId pkId = new PaperKeywordId(paper.getPaperId(), keyword.getKeywordId());
            if (paperKeywordRepository.findById(pkId).isEmpty()) {
                Double score = concept.getScore();
                paperKeywordRepository.save(PaperKeyword.builder()
                        .id(pkId)
                        .paper(paper)
                        .keyword(keyword)
                        .relevanceScore(score)
                        .build());
            }

            keywordTexts.add(text);
        }

        log.debug("Saved {} concepts as keywords for paper {}", keywordTexts.size(), paper.getPaperId());
        return keywordTexts;
    }

    /**
     * Lưu paper vào Neo4j graph để phục vụ tìm kiếm graph-based.
     */
    private void savePaperToNeo4j(ResearchPaper paper, List<String> keywordTexts) {
        try {
            graphService.savePaperWithKeywords(
                    paper.getPaperId().toString(),
                    paper.getTitle(),
                    paper.getDoi(),
                    paper.getPubYear() != null ? paper.getPubYear().intValue() : null,
                    keywordTexts
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

        return doi.trim()
                .replaceFirst("(?i)^https?://doi\\.org/", "")
                .replaceFirst("(?i)^doi:", "");
    }

    @Scheduled(fixedRate = 60000)
    public void scheduledSync() {
        runAutoSync();
    }

    // ============================================
    //  AUTO-SYNC KEYWORD MANAGEMENT
    // ============================================

    @Override
    @Transactional
    public AutoSyncKeyword addAutoSyncKeyword(String keyword, int intervalMinutes) {
        AutoSyncKeyword autoSyncKeyword = AutoSyncKeyword.builder()
                .keyword(keyword.trim())
                .intervalMinutes(Math.max(1, intervalMinutes))
                .enabled(true)
                .build();
        log.info("Added auto-sync keyword: '{}' (every {} min)", keyword, intervalMinutes);
        return autoSyncKeywordRepository.save(autoSyncKeyword);
    }

    @Override
    public List<AutoSyncKeyword> getAutoSyncKeywords() {
        return autoSyncKeywordRepository.findAll();
    }

    @Override
    @Transactional
    public AutoSyncKeyword updateAutoSyncKeyword(UUID id, String keyword, Integer intervalMinutes, Boolean enabled) {
        AutoSyncKeyword existing = autoSyncKeywordRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Auto-sync keyword not found: " + id));

        if (keyword != null && !keyword.isBlank()) {
            existing.setKeyword(keyword.trim());
        }
        if (intervalMinutes != null) {
            existing.setIntervalMinutes(Math.max(1, intervalMinutes));
        }
        if (enabled != null) {
            existing.setEnabled(enabled);
        }

        log.info("Updated auto-sync keyword {}: keyword='{}', interval={}min, enabled={}",
                id, existing.getKeyword(), existing.getIntervalMinutes(), existing.getEnabled());
        return autoSyncKeywordRepository.save(existing);
    }

    @Override
    @Transactional
    public void deleteAutoSyncKeyword(UUID id) {
        if (!autoSyncKeywordRepository.existsById(id)) {
            throw new RuntimeException("Auto-sync keyword not found: " + id);
        }
        autoSyncKeywordRepository.deleteById(id);
        log.info("Deleted auto-sync keyword: {}", id);
    }

    @Override
    public void runAutoSync() {
        List<AutoSyncKeyword> keywords = autoSyncKeywordRepository.findByEnabledTrue();

        if (keywords.isEmpty()) {
            // Seed defaults if table is empty
            seedDefaultAutoSyncKeywords();
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        for (AutoSyncKeyword kw : keywords) {
            if (kw.getLastSyncedAt() == null
                    || kw.getLastSyncedAt().plusMinutes(kw.getIntervalMinutes()).isBefore(now)) {

                log.info("Auto-syncing keyword: '{}' (interval: {} min)", kw.getKeyword(), kw.getIntervalMinutes());
                try {
                    syncFromOpenAlex(kw.getKeyword(), 50);
                    kw.setLastSyncedAt(LocalDateTime.now());
                    autoSyncKeywordRepository.save(kw);
                } catch (Exception e) {
                    log.error("Auto-sync failed for keyword '{}': {}", kw.getKeyword(), e.getMessage());
                }
            }
        }
    }

    private void seedDefaultAutoSyncKeywords() {
        log.info("No auto-sync keywords configured, seeding defaults");
        addAutoSyncKeyword("artificial intelligence", 1440); // daily
        addAutoSyncKeyword("machine learning", 1440);        // daily
    }
}
