package com.sra.journal_tracking.service.impl;

import com.sra.journal_tracking.dto.sync.OpenAlexResponseDTO;
import com.sra.journal_tracking.dto.sync.SemanticScholarResponseDTO;
import com.sra.journal_tracking.entity.jpa.ApiSource;
import com.sra.journal_tracking.entity.jpa.Author;
import com.sra.journal_tracking.entity.jpa.PaperAuthor;
import com.sra.journal_tracking.entity.jpa.PaperAuthorId;
import com.sra.journal_tracking.entity.jpa.ResearchPaper;
import com.sra.journal_tracking.entity.jpa.SyncLog;
import com.sra.journal_tracking.repository.jpa.ApiSourceRepository;
import com.sra.journal_tracking.repository.jpa.AuthorRepository;
import com.sra.journal_tracking.repository.jpa.PaperAuthorRepository;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataSyncServiceImpl implements DataSyncService {

    private final ResearchPaperRepository researchPaperRepository;
    private final AuthorRepository authorRepository;
    private final PaperAuthorRepository paperAuthorRepository;
    private final ApiSourceRepository apiSourceRepository;
    private final SyncLogRepository syncLogRepository;
    private final GraphService graphService;
    private final RestTemplate restTemplate;

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
                    savePaperToNeo4j(savedPaper, query);

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
                    .queryParam("select", "id,doi,title,display_name,publication_year,publication_date,cited_by_count,abstract_inverted_index,open_access,authorships")
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

                    // Lưu vào Neo4j graph để tìm kiếm lần sau nhanh hơn
                    savePaperToNeo4j(savedPaper, query);

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
     * Lưu paper vào Neo4j graph để phục vụ tìm kiếm graph-based.
     * Dùng search query làm keyword tag.
     */
    private void savePaperToNeo4j(ResearchPaper paper, String searchQuery) {
        try {
            List<String> keywords = List.of(searchQuery.trim());
            graphService.savePaperWithKeywords(
                    paper.getPaperId().toString(),
                    paper.getTitle(),
                    paper.getDoi(),
                    paper.getPubYear() != null ? paper.getPubYear().intValue() : null,
                    keywords
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

    @Scheduled(cron = "0 0 2 * * ?")
    public void scheduledSync() {
        syncFromOpenAlex("artificial intelligence", 100);
        syncFromOpenAlex("machine learning", 100);
    }
}
