package com.sra.journal_tracking.service.impl;

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
import com.sra.journal_tracking.repository.jpa.ResearchPaperRepository;
import com.sra.journal_tracking.repository.jpa.SyncLogRepository;
import com.sra.journal_tracking.service.DataSyncService;
import com.sra.journal_tracking.service.GraphService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
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

    @Override
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
                    savePaperToNeo4j(savedPaper, List.of(query.trim()));

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
    public SyncLog syncFromOpenAlex(String query, int limit) {
        log.info("Starting OpenAlex sync for query: {}", query);

        ApiSource source = getOrCreateOpenAlexSource();
        SyncLog syncLog = createRunningSyncLog(source, true);

        try {
            int perPage = Math.min(100, Math.max(1, limit));
            LocalDate today = LocalDate.now();
            int currentYear = today.getYear();
            int startYear = currentYear - RECENT_PUBLICATION_YEAR_WINDOW + 1;
            String openAlexSearchQuery = normalizeOpenAlexSearchQuery(query);
            String url = UriComponentsBuilder
                    .fromHttpUrl(source.getBaseUrl() + "/works")
                    .queryParam("search", openAlexSearchQuery)
                    .queryParam("filter", "from_publication_date:" + startYear + "-01-01,to_publication_date:" + today)
                    .queryParam("sort", "publication_date:desc")
                    .queryParam("per-page", perPage)
                    .queryParam("select", "id,doi,title,display_name,publication_year,publication_date,cited_by_count,abstract_inverted_index,open_access,primary_location,topics,keywords,authorships")
                    .build()
                    .encode()
                    .toUriString();

            OpenAlexResponseDTO response = restTemplate.getForObject(url, OpenAlexResponseDTO.class);

            if (response != null && response.getResults() != null) {
                int insertedCount = 0;
                syncLog.setPapersFetched(response.getResults().size());

                for (OpenAlexResponseDTO.OpenAlexWorkDTO work : response.getResults()) {
                    if (!isRecentPublication(work.getPublicationYear(), work.getPublicationDate(), startYear, currentYear, today)) {
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
                            .build();

                    setPublicationDate(newPaper, work.getPublicationDate());

                    ResearchPaper savedPaper = researchPaperRepository.save(newPaper);
                    insertedCount++;

                    // Cache paper-keyword links in Neo4j for graph search.
                    List<String> keywords = saveOpenAlexKeywords(savedPaper, work, query);
                    savePaperToNeo4j(savedPaper, keywords);

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
        if (publicationYear == null) {
            return false;
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
            return containsTokenVariant(normalizedPrimaryText, tokens.get(0));
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

    private void savePaperToNeo4j(ResearchPaper paper, List<String> keywords) {
        try {
            List<String> graphKeywords = keywords == null || keywords.isEmpty() ? List.of(paper.getTitle()) : keywords;
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
