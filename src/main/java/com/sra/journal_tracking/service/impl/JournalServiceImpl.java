package com.sra.journal_tracking.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sra.journal_tracking.dto.journal.JournalCategoryResponse;
import com.sra.journal_tracking.dto.journal.OpenAlexSourceResponseDTO;
import com.sra.journal_tracking.dto.journal.TopJournalDTO;
import com.sra.journal_tracking.entity.jpa.Journal;
import com.sra.journal_tracking.entity.jpa.ResearchField;
import com.sra.journal_tracking.repository.jpa.JournalRepository;
import com.sra.journal_tracking.repository.jpa.ResearchFieldRepository;
import com.sra.journal_tracking.service.JournalService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class JournalServiceImpl implements JournalService {

    private final ResearchFieldRepository fieldRepository;
    private final JournalRepository journalRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.openalex-email:}")
    private String openalexEmail;

    @Value("${app.openalex-api-key:}")
    private String openalexApiKey;

    private static final int TOP_JOURNALS_LIMIT = 6;
    private static final int MAX_CATEGORIES = 8;
    private static final int OPENALEX_SOURCES_PAGE_SIZE = 60;
    private static final int MAX_RETRIES = 3;

    /** Pattern to detect auto-generated duplicate field names like "Computer Science_2473030" */
    private static final Pattern DUPLICATE_FIELD_PATTERN = Pattern.compile(".*_\\d+$");

    /** Day-based cache: refresh once per day */
    private volatile LocalDate cacheDate;
    private volatile List<JournalCategoryResponse> cachedCategories;

    public JournalServiceImpl(ResearchFieldRepository fieldRepository,
                               JournalRepository journalRepository,
                               RestTemplate restTemplate,
                               ObjectMapper objectMapper) {
        this.fieldRepository = fieldRepository;
        this.journalRepository = journalRepository;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /** Pre-warm cache on startup — async, không block app. */
    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void warmUpCache() {
        try {
            Thread.sleep(3000); // chờ connection pool khởi tạo
            log.info("Pre-warming journal categories cache...");
            getJournalCategories();
            log.info("Journal categories cache warmed up");
        } catch (Exception e) {
            log.warn("Failed to pre-warm journal categories cache: {}", e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<JournalCategoryResponse> getJournalCategories() {
        LocalDate today = LocalDate.now();

        // Same day — return cached data
        if (cacheDate != null && cacheDate.equals(today) && cachedCategories != null) {
            log.debug("Returning cached journal categories for {}", today);
            return cachedCategories;
        }

        // Different day or first call — fetch fresh data
        log.info("Fetching fresh journal categories for {}", today);

        // Query 1: Get all top-level fields (filter duplicates in Java)
        List<ResearchField> allFields = fieldRepository.findByParentFieldIsNullAndIsTrackedTrue();
        List<ResearchField> topFields = allFields.stream()
                .filter(f -> !DUPLICATE_FIELD_PATTERN.matcher(f.getFieldName()).matches())
                .collect(Collectors.toList());

        log.info("Found {} clean top-level fields (filtered from {} raw)", topFields.size(), allFields.size());

        // Query 2: Get ALL active journals once, sorted by impact factor
        List<Journal> allJournals = journalRepository.findAllActiveWithFieldOrderByImpactFactorDesc();

        List<JournalCategoryResponse> result;

        if (topFields.isEmpty() || allJournals.isEmpty()) {
            // ── Fallback: DB has no data → query OpenAlex directly for top sources ──
            log.info("DB has no journal/field data (fields={}, journals={}), falling back to OpenAlex sources API",
                    topFields.size(), allJournals.size());
            result = fetchCategoriesFromOpenAlex();
        } else {
            // Group journals by field ID in-memory
            Map<UUID, List<Journal>> journalsByField = allJournals.stream()
                    .filter(j -> j.getField() != null)
                    .collect(Collectors.groupingBy(j -> j.getField().getFieldId()));

            // Build response — no extra DB calls
            result = topFields.stream()
                    .map(field -> buildCategoryResponse(field, journalsByField.getOrDefault(field.getFieldId(), Collections.emptyList())))
                    .collect(Collectors.toList());
        }

        // Update cache
        cachedCategories = result;
        cacheDate = today;

        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public JournalCategoryResponse getTopJournalsByField(UUID fieldId) {
        ResearchField field = fieldRepository.findById(fieldId)
                .orElseThrow(() -> new RuntimeException("Research field not found: " + fieldId));

        List<Journal> topJournals = journalRepository.findByField_FieldIdAndIsActiveTrueOrderByImpactFactorDesc(
                fieldId,
                org.springframework.data.domain.PageRequest.of(0, TOP_JOURNALS_LIMIT));

        return buildCategoryResponse(field, topJournals);
    }

    private JournalCategoryResponse buildCategoryResponse(ResearchField field, List<Journal> journals) {
        List<TopJournalDTO> topJournalDTOs = journals.stream()
                .limit(TOP_JOURNALS_LIMIT)
                .map(this::mapToTopJournalDTO)
                .collect(Collectors.toList());

        return JournalCategoryResponse.builder()
                .fieldId(field.getFieldId().toString())
                .fieldName(field.getFieldName())
                .description(field.getDescription())
                .journalCount(journals.size())
                .topJournals(topJournalDTOs)
                .build();
    }

    private TopJournalDTO mapToTopJournalDTO(Journal journal) {
        return TopJournalDTO.builder()
                .journalId(journal.getJournalId().toString())
                .journalName(journal.getJournalName())
                .publisher(journal.getPublisher())
                .issn(journal.getIssn())
                .impactFactor(journal.getImpactFactor())
                .quartile(journal.getQuartile())
                .build();
    }

    // ── OpenAlex fallback (when DB has no journal/field data) ──

    /**
     * Fetch top journals from OpenAlex /sources API, grouped by research field.
     * Called only when local DB has no data.
     */
    private List<JournalCategoryResponse> fetchCategoriesFromOpenAlex() {
        String authParam = (openalexApiKey != null && !openalexApiKey.isBlank())
                ? "&api_key=" + openalexApiKey
                : (openalexEmail != null && !openalexEmail.isBlank())
                ? "&mailto=" + openalexEmail : "";

        String url = "https://api.openalex.org/sources?sort=works_count:desc&per-page="
                + OPENALEX_SOURCES_PAGE_SIZE + authParam;
        log.info("Calling OpenAlex sources API: per-page={}", OPENALEX_SOURCES_PAGE_SIZE);

        String rawJson = fetchRawWithRetry(url);
        if (rawJson == null) {
            log.warn("OpenAlex sources API failed after {} retries", MAX_RETRIES);
            return Collections.emptyList();
        }

        List<OpenAlexSourceResponseDTO.SourceResult> sources;
        try {
            OpenAlexSourceResponseDTO response = objectMapper.readValue(rawJson,
                    OpenAlexSourceResponseDTO.class);
            sources = response.getResults();
        } catch (Exception e) {
            log.error("Failed to parse OpenAlex sources response: {}", e.getMessage());
            return Collections.emptyList();
        }

        if (sources == null || sources.isEmpty()) {
            log.warn("OpenAlex returned no sources");
            return Collections.emptyList();
        }

        log.info("OpenAlex returned {} sources, grouping by research field...", sources.size());

        // Group sources by their top-level field (from topics[0].field.display_name)
        Map<String, List<OpenAlexSourceResponseDTO.SourceResult>> byField = sources.stream()
                .filter(s -> s.getTopics() != null && !s.getTopics().isEmpty())
                .collect(Collectors.groupingBy(
                        s -> s.getTopics().get(0).getField() != null
                                ? s.getTopics().get(0).getField().getDisplayName()
                                : "Other",
                        LinkedHashMap::new,
                        Collectors.toList()));

        // Build category response for each field, limit to MAX_CATEGORIES
        List<JournalCategoryResponse> result = new ArrayList<>();
        int categoryCount = 0;
        for (Map.Entry<String, List<OpenAlexSourceResponseDTO.SourceResult>> entry : byField.entrySet()) {
            if (categoryCount >= MAX_CATEGORIES) break;

            String fieldName = entry.getKey();
            List<OpenAlexSourceResponseDTO.SourceResult> fieldSources = entry.getValue();

            List<TopJournalDTO> topJournals = fieldSources.stream()
                    .limit(TOP_JOURNALS_LIMIT)
                    .map(this::mapOpenAlexSourceToDTO)
                    .collect(Collectors.toList());

            result.add(JournalCategoryResponse.builder()
                    .fieldId(null) // OpenAlex doesn't give us internal UUID
                    .fieldName(fieldName)
                    .description("Top journals in " + fieldName + " (from OpenAlex)")
                    .journalCount(fieldSources.size())
                    .topJournals(topJournals)
                    .build());

            categoryCount++;
        }

        log.info("OpenAlex fallback: {} categories built from {} sources",
                result.size(), sources.size());
        return result;
    }

    private TopJournalDTO mapOpenAlexSourceToDTO(OpenAlexSourceResponseDTO.SourceResult source) {
        // Use 2yr_mean_citedness as approximate impact factor
        BigDecimal impactFactor = null;
        if (source.getSummaryStats() != null && source.getSummaryStats().getTwoYearMeanCitedness() != null) {
            impactFactor = BigDecimal.valueOf(source.getSummaryStats().getTwoYearMeanCitedness())
                    .setScale(2, RoundingMode.HALF_UP);
        }

        return TopJournalDTO.builder()
                .journalId(source.getId()) // OpenAlex ID (URL format)
                .journalName(source.getDisplayName())
                .publisher(source.getHostOrganizationName())
                .issn(source.getIssnL())
                .impactFactor(impactFactor)
                .quartile(null) // OpenAlex doesn't provide quartile
                .build();
    }

    /**
     * Retry logic — 3 attempts with 2s/4s/6s backoff.
     */
    private String fetchRawWithRetry(String url) {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                log.debug("OpenAlex sources attempt {}/{}", attempt + 1, MAX_RETRIES);
                return restTemplate.getForObject(url, String.class);
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                log.warn("OpenAlex sources attempt {}/{} failed: {}",
                        attempt + 1, MAX_RETRIES, msg);

                if (attempt < MAX_RETRIES - 1) {
                    long waitMs = (attempt + 1) * 2000L;
                    log.warn("Retrying in {}ms...", waitMs);
                    try {
                        Thread.sleep(waitMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    log.error("OpenAlex sources exhausted all {} retries. URL: {}", MAX_RETRIES, url);
                }
            }
        }
        return null;
    }
}
