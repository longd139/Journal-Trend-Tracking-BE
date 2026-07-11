package com.sra.journal_tracking.service;

import com.sra.journal_tracking.constants.KeywordConstants;
import com.sra.journal_tracking.dto.paper.AuthorDTO;
import com.sra.journal_tracking.dto.paper.KeywordDTO;
import com.sra.journal_tracking.dto.paper.PaperDetailResponseDTO;
import com.sra.journal_tracking.dto.paper.PaperSearchResultDTO;
import com.sra.journal_tracking.entity.jpa.PaperKeyword;
import com.sra.journal_tracking.entity.jpa.ResearchPaper;
import com.sra.journal_tracking.entity.jpa.User;
import com.sra.journal_tracking.entity.jpa.UserUsage;
import com.sra.journal_tracking.exception.UsageLimitExceededException;
import com.sra.journal_tracking.repository.jpa.ResearchPaperRepository;
import com.sra.journal_tracking.repository.jpa.SystemConfigRepository;
import com.sra.journal_tracking.repository.jpa.UserRepository;
import com.sra.journal_tracking.repository.jpa.UserUsageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
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
    private final OpenAlexFallbackSearchService openAlexFallbackSearchService;
    private final UserRepository userRepository;
    private final UserUsageRepository userUsageRepository;
    private final SystemConfigRepository systemConfigRepository;

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

        // Check & increment search usage for ACADEMIC_USER
        checkAndIncrementSearchUsage(userEmail);

        log.info("Search: keyword='{}', user='{}'", trimmedKeyword, userEmail);

        // Record search history (non-blocking)
        try { searchKeywordService.recordSearch(trimmedKeyword); } catch (Exception e) { log.warn("Record search failed: {}", e.getMessage()); }
        try { userSearchHistoryService.recordSearch(userEmail, trimmedKeyword, "KEYWORD"); } catch (Exception e) { log.warn("Record history failed: {}", e.getMessage()); }

        // ── Fetch directly from OpenAlex API ──
        log.info("Fetching from OpenAlex for '{}'", trimmedKeyword);
        PaperSearchResultDTO result;
        try {
            List<PaperDetailResponseDTO> papers = openAlexFallbackSearchService.searchTopCited(trimmedKeyword, safeLimit);

            // Also try relevance-sorted search and merge unique papers
            List<PaperDetailResponseDTO> relevancePapers = openAlexFallbackSearchService.searchNoYearFilter(trimmedKeyword, safeLimit);
            var seen = new java.util.HashSet<UUID>();
            List<PaperDetailResponseDTO> merged = new java.util.ArrayList<>();
            for (var p : papers) { if (seen.add(p.getPaperId())) merged.add(p); }
            for (var p : relevancePapers) { if (seen.add(p.getPaperId())) merged.add(p); }
            papers = merged.stream().limit(safeLimit).collect(java.util.stream.Collectors.toList());

            if (!papers.isEmpty()) {
                log.info("OpenAlex HIT: {} papers for '{}'", papers.size(), trimmedKeyword);
                result = PaperSearchResultDTO.builder()
                        .papers(papers)
                        .totalElements((long) papers.size())
                        .totalPages(1)
                        .currentPage(0)
                        .pageSize(papers.size())
                        .hasNext(false)
                        .hasPrev(false)
                        .build();
                searchResultCache.put(cacheKey, new CacheEntry<>(result));
                log.info("CACHE STORE: '{}' → {} papers (TTL=6h)", trimmedKeyword, papers.size());
                return result;
            }
        } catch (Exception e) {
            log.warn("OpenAlex search failed for '{}': {}", trimmedKeyword, e.getMessage());
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
                .viewCount(0L)
                .bookmarkCount(0L)
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

    /** Check and increment search usage for ACADEMIC_USER (mirrors PaperSearchServiceImpl). */
    private void checkAndIncrementSearchUsage(String userEmail) {
        User user = userRepository.findByEmail(userEmail).orElse(null);
        if (user == null || !"ACADEMIC_USER".equalsIgnoreCase(user.getRole().getRoleName())) {
            return;
        }
        String currentMonth = YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        UserUsage usage = userUsageRepository
                .findByUser_UserIdAndUsageMonth(user.getUserId(), currentMonth)
                .orElseGet(() -> {
                    UserUsage newUsage = UserUsage.builder()
                            .user(user)
                            .usageMonth(currentMonth)
                            .searchCount(0)
                            .viewCount(0)
                            .chartViewCount(0)
                            .build();
                    return userUsageRepository.save(newUsage);
                });

        int limit = systemConfigRepository.findByConfigKey("academic_monthly_search_limit")
                .map(cfg -> Integer.parseInt(cfg.getConfigValue()))
                .orElse(30);

        if (usage.getSearchCount() >= limit) {
            throw new UsageLimitExceededException(
                    "You have reached your monthly search limit (" + limit + "). Upgrade to Researcher?");
        }
        usage.setSearchCount(usage.getSearchCount() + 1);
        userUsageRepository.save(usage);
    }
}
