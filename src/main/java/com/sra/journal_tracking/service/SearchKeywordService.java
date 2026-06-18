package com.sra.journal_tracking.service;

import com.sra.journal_tracking.constants.KeywordConstants;
import com.sra.journal_tracking.dto.keyword.HotKeywordResponse;
import com.sra.journal_tracking.entity.jpa.SearchKeyword;
import com.sra.journal_tracking.repository.jpa.SearchKeywordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchKeywordService {

    private final SearchKeywordRepository searchKeywordRepository;
    private final GroqService groqService;

    /**
     * Record a search keyword for hot-keywords tracking.
     * Upserts: increments search count if keyword already exists, creates new row otherwise.
     * Runs in a new transaction to commit independently from the calling search transaction.
     *
     * @param keywordText the raw keyword text entered by the user
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSearch(String keywordText) {
        String normalized = keywordText.toLowerCase().trim();
        // Truncate in the tracking layer as well
        if (normalized.length() > KeywordConstants.MAX_KEYWORD_LENGTH) {
            normalized = normalized.substring(0, KeywordConstants.MAX_KEYWORD_LENGTH);
        }
        String original = keywordText.trim();
        if (original.length() > KeywordConstants.MAX_KEYWORD_LENGTH) {
            original = original.substring(0, KeywordConstants.MAX_KEYWORD_LENGTH);
        }

        Optional<SearchKeyword> existing = searchKeywordRepository.findByNormalizedText(normalized);

        if (existing.isPresent()) {
            SearchKeyword sk = existing.get();
            sk.setSearchCount(sk.getSearchCount() + 1);
            sk.setLastSearchedAt(LocalDateTime.now());
            searchKeywordRepository.save(sk);
            log.debug("Incremented search count for '{}' to {}", normalized, sk.getSearchCount());
        } else {
            SearchKeyword newRecord = SearchKeyword.builder()
                    .keywordText(original)
                    .normalizedText(normalized)
                    .searchCount(1)
                    .lastSearchedAt(LocalDateTime.now())
                    .createdAt(LocalDateTime.now())
                    .build();
            searchKeywordRepository.save(newRecord);
            log.debug("Created new search keyword record for '{}'", normalized);
        }
    }

    /**
     * Get the most trending/hot keywords.
     * First tries to fetch from Groq AI (external trending keywords).
     * Falls back to internal DB search history if Groq is unavailable.
     *
     * @param limit maximum number of hot keywords to return
     * @return list of HotKeywordResponse DTOs
     */
    public List<HotKeywordResponse> getHotKeywords(int limit) {
        // Try Groq AI first for real trending research keywords
        try {
            List<String> aiKeywords = groqService.getHotKeywords(limit);
            if (aiKeywords != null && !aiKeywords.isEmpty()) {
                log.info("Returning {} hot keywords from Groq AI", aiKeywords.size());
                return aiKeywords.stream()
                        .map(k -> HotKeywordResponse.builder()
                                .keywordText(k)
                                .source("AI")
                                .build())
                        .toList();
            }
        } catch (Exception e) {
            log.warn("Failed to fetch hot keywords from Groq AI, falling back to DB: {}", e.getMessage());
        }

        // Fallback: return most-searched keywords from internal DB
        log.info("Falling back to internal DB for hot keywords");
        return searchKeywordRepository
                .findAllByOrderBySearchCountDesc(PageRequest.of(0, limit))
                .stream()
                .map(sk -> HotKeywordResponse.builder()
                        .keywordText(sk.getKeywordText())
                        .searchCount(sk.getSearchCount())
                        .source("Internal")
                        .build())
                .toList();
    }
}
