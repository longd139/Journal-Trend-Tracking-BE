package com.sra.journal_tracking.service.impl;

import com.sra.journal_tracking.dto.journal.JournalCategoryResponse;
import com.sra.journal_tracking.dto.journal.TopJournalDTO;
import com.sra.journal_tracking.entity.jpa.Journal;
import com.sra.journal_tracking.entity.jpa.ResearchField;
import com.sra.journal_tracking.repository.jpa.JournalRepository;
import com.sra.journal_tracking.repository.jpa.ResearchFieldRepository;
import com.sra.journal_tracking.service.JournalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class JournalServiceImpl implements JournalService {

    private final ResearchFieldRepository fieldRepository;
    private final JournalRepository journalRepository;

    private static final int TOP_JOURNALS_LIMIT = 6;

    /** Pattern to detect auto-generated duplicate field names like "Computer Science_2473030" */
    private static final Pattern DUPLICATE_FIELD_PATTERN = Pattern.compile(".*_\\d+$");

    /** Day-based cache: refresh once per day */
    private volatile LocalDate cacheDate;
    private volatile List<JournalCategoryResponse> cachedCategories;

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

        // Group journals by field ID in-memory
        Map<UUID, List<Journal>> journalsByField = allJournals.stream()
                .filter(j -> j.getField() != null)
                .collect(Collectors.groupingBy(j -> j.getField().getFieldId()));

        // Build response — no extra DB calls
        List<JournalCategoryResponse> result = topFields.stream()
                .map(field -> buildCategoryResponse(field, journalsByField.getOrDefault(field.getFieldId(), Collections.emptyList())))
                .collect(Collectors.toList());

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
}
