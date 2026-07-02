package com.sra.journal_tracking.service.impl;

import com.sra.journal_tracking.dto.author.SuggestedAuthorResponse;
import com.sra.journal_tracking.entity.jpa.Author;
import com.sra.journal_tracking.entity.jpa.ResearchField;
import com.sra.journal_tracking.repository.jpa.AuthorRepository;
import com.sra.journal_tracking.repository.jpa.PaperAuthorRepository;
import com.sra.journal_tracking.repository.jpa.ResearchFieldRepository;
import com.sra.journal_tracking.service.AuthorSuggestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthorSuggestionServiceImpl implements AuthorSuggestionService {

    private final AuthorRepository authorRepository;
    private final PaperAuthorRepository paperAuthorRepository;
    private final ResearchFieldRepository fieldRepository;

    private static final int SUGGESTED_LIMIT = 12;

    /** Pre-warm cache on startup — async, không block app. */
    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void warmUpCache() {
        try {
            Thread.sleep(3000); // chờ connection pool
            log.info("Pre-warming suggested authors cache...");
            getSuggestedAuthors();
            log.info("Suggested authors cache warmed up");
        } catch (Exception e) {
            log.warn("Failed to pre-warm suggested authors cache: {}", e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "search:suggestedAuthors", cacheManager = "searchCacheManager",
               key = "'dailyTopAuthors'", unless = "#result == null || #result.isEmpty()")
    public List<SuggestedAuthorResponse> getSuggestedAuthors() {
        log.info("Fetching fresh suggested authors from database");

        // Query: Top authors by total citations
        List<Author> topAuthors = authorRepository.findAllByOrderByTotalCitationsDesc(
                PageRequest.of(0, SUGGESTED_LIMIT));

        // Build response — for each author, find top field
        return topAuthors.stream()
                .map(this::buildAuthorResponse)
                .toList();
    }

    private SuggestedAuthorResponse buildAuthorResponse(Author author) {
        // Find author's top field
        List<UUID> topFieldIds = paperAuthorRepository.findTopFieldIdsByAuthorId(author.getAuthorId());

        String topFieldName = null;
        String topFieldId = null;
        if (!topFieldIds.isEmpty()) {
            UUID fieldId = topFieldIds.get(0);
            topFieldId = fieldId.toString();
            topFieldName = fieldRepository.findById(fieldId)
                    .map(ResearchField::getFieldName)
                    .orElse(null);
        }

        long paperCount = paperAuthorRepository.countByAuthor_AuthorId(author.getAuthorId());

        return SuggestedAuthorResponse.builder()
                .authorId(author.getAuthorId().toString())
                .fullName(author.getFullName())
                .affiliation(author.getAffiliation())
                .hIndex(author.getHIndex())
                .totalCitations(author.getTotalCitations())
                .topField(topFieldName)
                .topFieldId(topFieldId)
                .paperCount(paperCount)
                .build();
    }
}
