package com.sra.journal_tracking.service;

import com.sra.journal_tracking.constants.KeywordConstants;
import com.sra.journal_tracking.dto.paper.AuthorDTO;
import com.sra.journal_tracking.dto.paper.KeywordDTO;
import com.sra.journal_tracking.dto.paper.PaperDetailResponseDTO;
import com.sra.journal_tracking.dto.paper.PaperSearchResultDTO;
import com.sra.journal_tracking.entity.jpa.PaperKeyword;
import com.sra.journal_tracking.entity.jpa.ResearchPaper;
import com.sra.journal_tracking.repository.jpa.ResearchPaperRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Graph-based paper search flow.
 * Neo4j provides cached paper IDs, SQL Server provides full paper data, and
 * OpenAlex is queued as a background fallback when no cached result exists.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaperSearchOrchestrator {
    private static final int MAX_SEARCH_RESULTS = 50;
    private static final double MIN_PRIMARY_KEYWORD_SCORE = 0.55d;
    private static final long CACHE_TTL_MS = 6 * 60 * 60 * 1000; // 6 giờ

    // ── Manual in-memory cache ──
    private static class CacheEntry<T> {
        final T data;
        final long expiryTime;
        CacheEntry(T data) { this.data = data; this.expiryTime = System.currentTimeMillis() + CACHE_TTL_MS; }
        boolean isExpired() { return System.currentTimeMillis() > expiryTime; }
    }
    private final ConcurrentHashMap<String, CacheEntry<PaperSearchResultDTO>> searchResultCache = new ConcurrentHashMap<>();

    private final GraphService graphService;
    private final DataSyncService dataSyncService;
    private final SearchKeywordService searchKeywordService;
    private final UserSearchHistoryService userSearchHistoryService;
    private final ResearchPaperRepository researchPaperRepository;

    @Transactional
    public PaperSearchResultDTO searchByKeyword(String keyword, String userEmail) {
        return searchByKeyword(keyword, userEmail, MAX_SEARCH_RESULTS);
    }

    @Transactional
    public PaperSearchResultDTO searchByKeyword(String keyword, String userEmail, int resultLimit) {
        String trimmedKeyword = keyword.trim();
        if (trimmedKeyword.isEmpty()) {
            throw new IllegalArgumentException("Keyword cannot be empty");
        }
        int safeLimit = Math.max(1, Math.min(resultLimit, MAX_SEARCH_RESULTS));

        // ── Cache check ──
        String cacheKey = trimmedKeyword.toLowerCase();
        CacheEntry<PaperSearchResultDTO> cached = searchResultCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            log.info("CACHE HIT: orchestrator '{}' → {} papers (from cache)", trimmedKeyword,
                    cached.data.getPapers() != null ? cached.data.getPapers().size() : 0);
            return cached.data;
        }
        if (cached != null) {
            searchResultCache.remove(cacheKey);
        }

        // Truncate keyword to max allowed length
        if (trimmedKeyword.length() > KeywordConstants.MAX_KEYWORD_LENGTH) {
            log.warn("Keyword truncated from {} chars to {} chars", trimmedKeyword.length(), KeywordConstants.MAX_KEYWORD_LENGTH);
            trimmedKeyword = trimmedKeyword.substring(0, KeywordConstants.MAX_KEYWORD_LENGTH);
        }

        log.info("Graph search: keyword='{}', user='{}'", trimmedKeyword, userEmail);

        // Record the search keyword for hot-keywords tracking (non-blocking)
        try {
            searchKeywordService.recordSearch(trimmedKeyword);
        } catch (Exception e) {
            log.warn("Failed to record search keyword '{}': {}", trimmedKeyword, e.getMessage());
        }

        // Record per-user search history for zero-state recent-searches feature
        try {
            userSearchHistoryService.recordSearch(userEmail, trimmedKeyword, "KEYWORD");
        } catch (Exception e) {
            log.warn("Failed to record user search history '{}': {}", trimmedKeyword, e.getMessage());
        }

        PaperSearchResultDTO result;

        List<String> neo4jPaperIds = graphService.searchPapersByKeyword(trimmedKeyword);

        if (!neo4jPaperIds.isEmpty()) {
            log.info("Neo4j HIT: {} papers found for '{}'", neo4jPaperIds.size(), trimmedKeyword);

            List<ResearchPaper> sqlPapers = filterRelevantPapers(fetchPapersFromSql(neo4jPaperIds), trimmedKeyword);

            if (!sqlPapers.isEmpty()) {
                log.info("SQL MATCH: {} papers valid in SQL", sqlPapers.size());
                result = mapToSearchResultDTO(sqlPapers);
                searchResultCache.put(cacheKey, new CacheEntry<>(result));
                log.info("CACHE STORE: orchestrator '{}' → {} papers (TTL=1h)", trimmedKeyword, sqlPapers.size());
                return result;
            }

            log.warn("Neo4j STALE: {} IDs found in Neo4j but 0 in SQL. Cleaning up & falling back to SQL full-text.",
                    neo4jPaperIds.size());
            graphService.deleteStalePapers(neo4jPaperIds);
        } else {
            log.info("Neo4j MISS for '{}'", trimmedKeyword);
        }

        // ── SQL full-text fallback (no external API call) ──
        log.info("Trying SQL full-text search for '{}'", trimmedKeyword);
        List<ResearchPaper> sqlResults = researchPaperRepository.findTopCitedByKeyword(
                trimmedKeyword, org.springframework.data.domain.PageRequest.of(0, safeLimit));

        if (!sqlResults.isEmpty()) {
            log.info("SQL FULL-TEXT HIT: {} papers found for '{}'", sqlResults.size(), trimmedKeyword);
            result = mapToSearchResultDTO(sqlResults);
            searchResultCache.put(cacheKey, new CacheEntry<>(result));
            log.info("CACHE STORE: orchestrator '{}' → {} papers (TTL=1h)", trimmedKeyword, sqlResults.size());

            // Background async sync to enrich Neo4j for next search
            try {
                dataSyncService.syncFromOpenAlexAsync(trimmedKeyword, safeLimit);
            } catch (Exception e) {
                log.warn("Background sync failed for '{}': {}", trimmedKeyword, e.getMessage());
            }

            return result;
        }

        // Nothing found — trigger background sync for future searches
        log.info("No papers found for '{}' in any source. Triggering background sync.", trimmedKeyword);
        try {
            dataSyncService.syncFromOpenAlexAsync(trimmedKeyword, safeLimit);
        } catch (Exception e) {
            log.warn("Background sync trigger failed for '{}': {}", trimmedKeyword, e.getMessage());
        }

        return buildEmptyResult();
    }

    // ============================================
    //  PRIVATE HELPERS
    // ============================================

    private List<ResearchPaper> fetchPapersFromSql(List<String> paperIdStrings) {
        List<UUID> uuids = paperIdStrings.stream()
                .map(UUID::fromString)
                .collect(Collectors.toList());

        List<ResearchPaper> papers = researchPaperRepository.findAllById(uuids);

        papers.sort((a, b) -> {
            int idxA = uuids.indexOf(a.getPaperId());
            int idxB = uuids.indexOf(b.getPaperId());
            return Integer.compare(idxA, idxB);
        });

        return papers;
    }

    private PaperSearchResultDTO mapToSearchResultDTO(List<ResearchPaper> papers) {
        List<PaperDetailResponseDTO> dtos = papers.stream()
                .map(this::mapToDetailDTO)
                .collect(Collectors.toList());

        return PaperSearchResultDTO.builder()
                .papers(dtos)
                .totalElements((long) dtos.size())
                .totalPages(1)
                .currentPage(0)
                .pageSize(dtos.size())
                .hasNext(false)
                .hasPrev(false)
                .build();
    }

    private PaperDetailResponseDTO mapToDetailDTO(ResearchPaper paper) {
        List<AuthorDTO> authors = paper.getAuthors() != null ? paper.getAuthors().stream()
                .map(pa -> AuthorDTO.builder()
                        .fullName(pa.getAuthor().getFullName())
                        .affiliation(pa.getAuthor().getAffiliation())
                        .hIndex(pa.getAuthor().getHIndex())
                        .totalCitations(pa.getAuthor().getTotalCitations())
                        .authorOrder(pa.getAuthorOrder())
                        .build())
                .collect(Collectors.toList()) : new ArrayList<>();

        List<KeywordDTO> keywords = paper.getKeywords() != null ? paper.getKeywords().stream()
                .filter(pk -> !isSyntheticKeyword(pk))
                .map(pk -> KeywordDTO.builder()
                        .keywordText(pk.getKeyword().getKeywordText())
                        .relevanceScore(pk.getRelevanceScore())
                        .build())
                .collect(Collectors.toList()) : new ArrayList<>();

        String sourceUrl = paper.getDoi() != null ? "https://doi.org/" + paper.getDoi() : null;
        Boolean pdfAvailable = Boolean.TRUE.equals(paper.getIsOpenAccess())
                || (paper.getPdfUrl() != null && !paper.getPdfUrl().isBlank());
        String downloadUrl = (paper.getPdfUrl() != null && !paper.getPdfUrl().isBlank())
                ? paper.getPdfUrl() : sourceUrl;

        return PaperDetailResponseDTO.builder()
                .paperId(paper.getPaperId())
                .title(paper.getTitle())
                .abstractText(paper.getAbstractText())
                .doi(paper.getDoi())
                .pubYear(paper.getPubYear())
                .pubDate(paper.getPubDate())
                .citationCount(paper.getCitationCount())
                .isOpenAccess(paper.getIsOpenAccess())
                .journalName(paper.getJournal() != null ? paper.getJournal().getJournalName() : null)
                .journalId(paper.getJournal() != null ? paper.getJournal().getJournalId() : null)
                .fieldName(paper.getField() != null ? paper.getField().getFieldName() : null)
                .fieldId(paper.getField() != null ? paper.getField().getFieldId() : null)
                .authors(authors)
                .keywords(keywords)
                .sourceUrl(sourceUrl)
                .pdfAvailable(pdfAvailable)
                .downloadUrl(downloadUrl)
                .pdfUrl(paper.getPdfUrl())
                .rating(0.0)
                .downloadCount(0)
                .commentCount(0)
                .createdAt(paper.getCreatedAt())
                .build();
    }

    private PaperSearchResultDTO buildEmptyResult() {
        return PaperSearchResultDTO.builder()
                .papers(List.of())
                .totalElements(0L)
                .totalPages(0)
                .currentPage(0)
                .pageSize(0)
                .hasNext(false)
                .hasPrev(false)
                .build();
    }

    private List<ResearchPaper> filterRelevantPapers(List<ResearchPaper> papers, String query) {
        List<String> tokens = extractSearchTokens(query);
        if (tokens.isEmpty()) {
            return papers;
        }
        String normalizedQuery = normalizeSearchText(query);
        return papers.stream()
                .filter(paper -> matchesAllTokens(paper, tokens, normalizedQuery))
                .collect(Collectors.toList());
    }

    private boolean matchesAllTokens(ResearchPaper paper, List<String> tokens, String normalizedQuery) {
        String primaryText = buildPrimarySearchableText(paper, normalizedQuery);
        if (tokens.size() == 1) {
            // Single-token: check both primary text AND abstract
            StringBuilder fullText = new StringBuilder(primaryText);
            append(fullText, paper.getAbstractText());
            return containsTokenVariant(normalizeSearchText(fullText.toString()), tokens.get(0));
        }

        String fullText = buildFullSearchableText(paper, normalizedQuery);
        return tokens.stream().allMatch(token -> containsTokenVariant(fullText, token))
                && tokens.stream().anyMatch(token -> containsTokenVariant(primaryText, token));
    }

    private String buildPrimarySearchableText(ResearchPaper paper, String normalizedQuery) {
        StringBuilder text = new StringBuilder();
        append(text, paper.getTitle());
        if (paper.getJournal() != null) {
            append(text, paper.getJournal().getJournalName());
        }
        if (paper.getField() != null) {
            append(text, paper.getField().getFieldName());
        }
        if (paper.getKeywords() != null) {
            paper.getKeywords().forEach(pk -> {
                if (pk.getKeyword() != null && isPrimaryKeyword(pk)) {
                    String keywordText = pk.getKeyword().getKeywordText();
                    if (!normalizeSearchText(keywordText).equals(normalizedQuery)) {
                        append(text, keywordText);
                    }
                }
            });
        }
        return normalizeSearchText(text.toString());
    }

    private String buildFullSearchableText(ResearchPaper paper, String normalizedQuery) {
        StringBuilder text = new StringBuilder(buildPrimarySearchableText(paper, normalizedQuery));
        append(text, paper.getAbstractText());
        append(text, paper.getDoi());
        if (paper.getAuthors() != null) {
            paper.getAuthors().forEach(pa -> {
                if (pa.getAuthor() != null) {
                    append(text, pa.getAuthor().getFullName());
                }
            });
        }
        return normalizeSearchText(text.toString());
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
                .collect(Collectors.toList());
    }

    private boolean isSearchStopWord(String token) {
        return token.equals("and") || token.equals("or") || token.equals("the") || token.equals("of")
                || token.equals("in") || token.equals("on") || token.equals("for") || token.equals("to")
                || token.equals("a") || token.equals("an");
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
        return variants.stream().distinct().collect(Collectors.toList());
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

    private boolean isSyntheticKeyword(PaperKeyword paperKeyword) {
        return paperKeyword.getRelevanceScore() != null
                && Double.compare(paperKeyword.getRelevanceScore(), 1.0d) == 0;
    }

    private boolean isPrimaryKeyword(PaperKeyword paperKeyword) {
        return !isSyntheticKeyword(paperKeyword)
                && paperKeyword.getRelevanceScore() != null
                && paperKeyword.getRelevanceScore() >= MIN_PRIMARY_KEYWORD_SCORE;
    }
}
