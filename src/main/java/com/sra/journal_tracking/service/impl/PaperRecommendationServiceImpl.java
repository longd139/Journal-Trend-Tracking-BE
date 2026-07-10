package com.sra.journal_tracking.service.impl;

import com.sra.journal_tracking.dto.paper.AuthorDTO;
import com.sra.journal_tracking.dto.paper.KeywordDTO;
import com.sra.journal_tracking.dto.paper.PaperDetailResponseDTO;
import com.sra.journal_tracking.dto.recommendation.RecommendationResponseDTO;
import com.sra.journal_tracking.dto.recommendation.RecommendationResultDTO;
import com.sra.journal_tracking.entity.jpa.*;
import com.sra.journal_tracking.exception.AppException;
import com.sra.journal_tracking.exception.ErrorCode;
import com.sra.journal_tracking.repository.jpa.*;
import com.sra.journal_tracking.service.GraphService;
import com.sra.journal_tracking.service.KeywordExpansionService;
import com.sra.journal_tracking.service.OpenAlexFallbackSearchService;
import com.sra.journal_tracking.service.PaperRecommendationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Hybrid recommendation engine combining content-based filtering
 * (from Neo4j graph + SQL keyword matching) and collaborative filtering
 * (from similar users' bookmarks), with cold-start fallback to trending papers.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaperRecommendationServiceImpl implements PaperRecommendationService {

    private static final int MAX_INTEREST_KEYWORDS = 15;
    private static final int MAX_CANDIDATE_KEYWORDS = 5;
    private static final int MAX_CANDIDATES_PER_KEYWORD = 10;
    private static final int MAX_COLLAB_USERS = 10;
    private static final int MAX_COLD_START_RESULTS = 20;
    private static final long CACHE_TTL_MS = 30 * 60 * 1000; // 30 minutes
    private static final long SIMILAR_CACHE_TTL_MS = 60 * 60 * 1000; // 1 hour
    private static final double MIN_PRIMARY_KEYWORD_SCORE = 0.55d;

    // ── Manual in-memory caches ──
    private static class CacheEntry<T> {
        final T data;
        final long expiryTime;
        CacheEntry(T data, long ttlMs) {
            this.data = data;
            this.expiryTime = System.currentTimeMillis() + ttlMs;
        }
        boolean isExpired() { return System.currentTimeMillis() > expiryTime; }
    }

    private final ConcurrentHashMap<String, CacheEntry<RecommendationResultDTO>> recCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CacheEntry<RecommendationResultDTO>> similarCache = new ConcurrentHashMap<>();

    private final UserRepository userRepository;
    private final UserSearchHistoryRepository userSearchHistoryRepository;
    private final BookmarkRepository bookmarkRepository;
    private final FollowRepository followRepository;
    private final ResearchPaperRepository researchPaperRepository;
    private final KeywordRepository keywordRepository;
    private final GraphService graphService;
    private final OpenAlexFallbackSearchService openAlexSearchService;
    private final KeywordExpansionService keywordExpansionService;

    // ═══════════════════════════════════════════════════════════
    //  Public API
    // ═══════════════════════════════════════════════════════════

    @Override
    public RecommendationResultDTO getPersonalizedRecommendations(String userEmail, int page, int size) {
        // DEBUG: bypass interest profile to isolate hang
        RecommendationResultDTO result = buildColdStartResult(page, size);
        String cacheKey = "rec:" + userEmail + ":" + page + ":" + size;
        recCache.put(cacheKey, new CacheEntry<>(result, CACHE_TTL_MS));
        return result;
    }

    // ORIGINAL
    private RecommendationResultDTO getPersonalizedRecommendations_ORIG(String userEmail, int page, int size) {
        String cacheKey = "rec:" + userEmail + ":" + page + ":" + size;
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        // Step 1: Build interest profile
        Map<String, Double> interestKeywords = buildInterestProfile(user);

        // Step 2: Cold-start check
        if (interestKeywords.isEmpty()) {
            RecommendationResultDTO result = buildColdStartResult(page, size);
            recCache.put(cacheKey, new CacheEntry<>(result, CACHE_TTL_MS));
            return result;
        }

        // Step 3: Content-based candidates
        Map<UUID, PaperScore> scoredPapers = new LinkedHashMap<>();
        generateContentBasedCandidates(interestKeywords, scoredPapers, user.getUserId());

        // Step 4: Collaborative candidates
        generateCollaborativeCandidates(interestKeywords, scoredPapers, user.getUserId());

        // Step 5: Remove already-bookmarked papers
        Set<UUID> bookmarkedIds = getBookmarkedPaperIds(user.getUserId());
        scoredPapers.keySet().removeAll(bookmarkedIds);

        // Step 5b: OpenAlex fallback if SQL returned nothing
        if (scoredPapers.isEmpty() && !interestKeywords.isEmpty()) {
            String topKeyword = interestKeywords.keySet().iterator().next();
            log.info("SQL returned no results — falling back to OpenAlex for '{}'", topKeyword);
            try {
                List<PaperDetailResponseDTO> openAlexPapers =
                        openAlexSearchService.searchTopCited(topKeyword, size * 2);
                for (PaperDetailResponseDTO dto : openAlexPapers) {
                    scoredPapers.put(dto.getPaperId(),
                            new PaperScore(3.0, "Trending on OpenAlex: '" + topKeyword + "'"));
                }
            } catch (Exception e) {
                log.warn("OpenAlex fallback failed: {}", e.getMessage());
            }
        }

        // Step 6: Rank, paginate, and build response
        RecommendationResultDTO result = buildPaginatedResult(scoredPapers, page, size);
        recCache.put(cacheKey, new CacheEntry<>(result, CACHE_TTL_MS));
        log.info("Generated {} recommendations for {} (page {})",
                result.getRecommendations().size(), userEmail, page);
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public RecommendationResultDTO getSimilarPapers(String userEmail, UUID paperId, int page, int size) {
        String cacheKey = "sim:" + paperId + ":" + page + ":" + size;
        CacheEntry<RecommendationResultDTO> cached = similarCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            log.debug("CACHE HIT: similar papers for {}", paperId);
            return cached.data;
        }

        ResearchPaper sourcePaper = researchPaperRepository.findByIdWithDetails(paperId)
                .orElseThrow(() -> new AppException(ErrorCode.PAPER_NOT_FOUND));

        Map<UUID, PaperScore> scoredPapers = new LinkedHashMap<>();

        // Strategy A: Same-field papers
        if (sourcePaper.getField() != null) {
            List<ResearchPaper> sameField = researchPaperRepository.findByFieldIdAndPaperIdNot(
                    sourcePaper.getField().getFieldId(), paperId,
                    PageRequest.of(0, size * 2));
            for (ResearchPaper p : sameField) {
                scoredPapers.merge(p.getPaperId(),
                        new PaperScore(5.0, "Same research field: " + sourcePaper.getField().getFieldName()),
                        PaperScore::merge);
            }
        }

        // Strategy B: Same-keyword papers via SQL
        if (sourcePaper.getKeywords() != null) {
            List<PaperKeyword> primaryKeywords = sourcePaper.getKeywords().stream()
                    .filter(pk -> !isSyntheticKeyword(pk))
                    .sorted(Comparator.comparing(PaperKeyword::getRelevanceScore,
                            Comparator.nullsLast(Comparator.reverseOrder())))
                    .limit(5)
                    .toList();

            for (PaperKeyword pk : primaryKeywords) {
                String kwText = pk.getKeyword().getKeywordText();
                try {
                    List<ResearchPaper> sqlPapers = researchPaperRepository.findTopCitedByKeyword(
                            kwText, PageRequest.of(0, 10));
                    for (ResearchPaper p : sqlPapers) {
                        if (!p.getPaperId().equals(paperId)) {
                            double relevance = pk.getRelevanceScore() != null ? pk.getRelevanceScore() : 0.5;
                            scoredPapers.merge(p.getPaperId(),
                                    new PaperScore(10.0 * relevance,
                                            "Shares keyword: " + pk.getKeyword().getKeywordText()),
                                    PaperScore::merge);
                        }
                    }
                } catch (Exception e) {
                    log.warn("SQL keyword search failed for '{}': {}", kwText, e.getMessage());
                }
            }
        }

        // Build result
        RecommendationResultDTO result = buildPaginatedResult(scoredPapers, page, size);
        similarCache.put(cacheKey, new CacheEntry<>(result, SIMILAR_CACHE_TTL_MS));
        log.info("Found {} similar papers for {}", result.getTotalElements(), paperId);
        return result;
    }

    // ═══════════════════════════════════════════════════════════
    //  Interest Profile Building
    // ═══════════════════════════════════════════════════════════

    /**
     * Build a weighted keyword interest profile from three signal sources:
     * search history, bookmarked papers, and followed targets.
     */
    private Map<String, Double> buildInterestProfile(User user) {
        Map<String, Double> profile = new LinkedHashMap<>();
        UUID userId = user.getUserId();
        LocalDateTime now = LocalDateTime.now();

        // Signal A: Search history (weight: recency-decayed, base 1.0)
        List<UserSearchHistory> searches = userSearchHistoryRepository
                .findByUser_UserIdOrderBySearchedAtDesc(userId, PageRequest.of(0, 30));
        for (UserSearchHistory s : searches) {
            if (!"KEYWORD".equals(s.getSearchType())) continue;
            String normalized = s.getSearchText().toLowerCase().trim();
            if (normalized.isEmpty() || normalized.length() < 2) continue;
            double daysAgo = ChronoUnit.DAYS.between(s.getSearchedAt(), now);
            double decay = 1.0 / (1.0 + daysAgo / 30.0); // 1.0 today → 0.5 at 30d → 0.25 at 90d
            profile.merge(normalized, 1.0 * decay, Double::sum);
        }

        // Signal B: Bookmarked papers (weight: 0.8 per paper's keywords)
        List<Bookmark> bookmarks = bookmarkRepository.findByUser_UserId(userId,
                PageRequest.of(0, 100)).getContent();
        for (Bookmark bm : bookmarks) {
            if (bm.getPaper() == null || bm.getPaper().getKeywords() == null) continue;
            for (PaperKeyword pk : bm.getPaper().getKeywords()) {
                if (isSyntheticKeyword(pk)) continue;
                String normalized = pk.getKeyword().getNormalizedText();
                if (normalized == null || normalized.length() < 2) continue;
                double daysAgo = ChronoUnit.DAYS.between(bm.getCreatedAt(), now);
                double decay = 1.0 / (1.0 + daysAgo / 60.0);
                profile.merge(normalized, 0.8 * decay, Double::sum);
            }
        }
        // Also add directly bookmarked keywords
        for (Bookmark bm : bookmarks) {
            if (bm.getKeyword() == null) continue;
            String normalized = bm.getKeyword().getNormalizedText();
            if (normalized == null || normalized.length() < 2) continue;
            profile.merge(normalized, 1.2, Double::sum);
        }

        // Signal C: Followed keywords/journals/topics (weight: 1.5)
        List<Follow> follows = followRepository.findByUser_UserId(userId);
        for (Follow f : follows) {
            if (f.getKeyword() != null) {
                String normalized = f.getKeyword().getNormalizedText();
                if (normalized != null && normalized.length() >= 2) {
                    profile.merge(normalized, 1.5, Double::sum);
                }
            }
            if (f.getTopic() != null) {
                String topicName = f.getTopic().getTopicName().toLowerCase().trim();
                if (topicName.length() >= 2) {
                    profile.merge(topicName, 1.5, Double::sum);
                }
            }
            // Journal follows: use journal name as interest signal
            if (f.getJournal() != null) {
                String journalName = f.getJournal().getJournalName().toLowerCase().trim();
                if (journalName.length() >= 2) {
                    profile.merge(journalName, 1.0, Double::sum);
                }
            }
        }

        // Sort by weight descending, keep top N
        return profile.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(MAX_INTEREST_KEYWORDS)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new));
    }

    // ═══════════════════════════════════════════════════════════
    //  Content-Based Candidate Generation
    // ═══════════════════════════════════════════════════════════

    private void generateContentBasedCandidates(Map<String, Double> interestKeywords,
                                                 Map<UUID, PaperScore> scoredPapers,
                                                 UUID userId) {
        List<Map.Entry<String, Double>> topKeywords = interestKeywords.entrySet().stream()
                .limit(MAX_CANDIDATE_KEYWORDS)
                .toList();

        for (var entry : topKeywords) {
            String keyword = entry.getKey();
            double weight = entry.getValue();

            // SQL: find papers by keyword text in title/abstract
            try {
                List<ResearchPaper> sqlPapers = researchPaperRepository.findTopCitedByKeyword(
                        keyword, PageRequest.of(0, MAX_CANDIDATES_PER_KEYWORD));
                for (ResearchPaper p : sqlPapers) {
                    scoredPapers.merge(p.getPaperId(),
                            new PaperScore(weight * 10.0,
                                    "Matches your interest: '" + keyword + "'"),
                            PaperScore::merge);
                }
            } catch (Exception e) {
                log.warn("SQL keyword search failed for '{}': {}", keyword, e.getMessage());
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Collaborative Filtering
    // ═══════════════════════════════════════════════════════════

    private void generateCollaborativeCandidates(Map<String, Double> interestKeywords,
                                                  Map<UUID, PaperScore> scoredPapers,
                                                  UUID userId) {
        // Get top keyword texts
        List<String> topTerms = interestKeywords.keySet().stream()
                .limit(5)
                .toList();

        if (topTerms.isEmpty()) return;

        // Find users who searched the same terms
        List<UUID> similarUserIds;
        try {
            similarUserIds = userSearchHistoryRepository.findUsersBySearchTerms(topTerms, userId);
        } catch (Exception e) {
            log.warn("Collaborative user lookup failed: {}", e.getMessage());
            return;
        }

        if (similarUserIds.isEmpty()) return;

        // Limit to top N similar users
        if (similarUserIds.size() > MAX_COLLAB_USERS) {
            similarUserIds = similarUserIds.subList(0, MAX_COLLAB_USERS);
        }

        // Get papers bookmarked by similar users (excluding current user's papers)
        Set<UUID> excludeIds = getBookmarkedPaperIds(userId);
        List<UUID> excludeList = new ArrayList<>(excludeIds);

        try {
            List<ResearchPaper> collabPapers = researchPaperRepository.findPapersBookmarkedByUsers(
                    similarUserIds, excludeList, (short) 2020,
                    PageRequest.of(0, MAX_CANDIDATES_PER_KEYWORD * 3));
            for (ResearchPaper p : collabPapers) {
                scoredPapers.merge(p.getPaperId(),
                        new PaperScore(5.0, "Readers like you also saved this"),
                        PaperScore::merge);
            }
        } catch (Exception e) {
            log.warn("Collaborative paper lookup failed: {}", e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Cold-Start Fallback
    // ═══════════════════════════════════════════════════════════

    private RecommendationResultDTO buildColdStartResult(int page, int size) {
        short currentYear = (short) java.time.Year.now().getValue();
        var paperPage = researchPaperRepository.findByPubYearBetween(
                (short) (currentYear - 2), currentYear,
                PageRequest.of(page, Math.min(size, MAX_COLD_START_RESULTS)));

        List<RecommendationResponseDTO> recommendations = paperPage.getContent().stream()
                .map(p -> RecommendationResponseDTO.builder()
                        .paper(mapToDetailDTO(p))
                        .reason(RecommendationResponseDTO.Reason.TRENDING)
                        .reasonDetail("Trending paper — explore more to get personalized recommendations")
                        .build())
                .collect(Collectors.toList());

        return RecommendationResultDTO.builder()
                .recommendations(recommendations)
                .totalElements((long) recommendations.size())
                .totalPages(recommendations.isEmpty() ? 0 : 1)
                .currentPage(page)
                .pageSize(recommendations.size())
                .hasNext(false)
                .hasPrev(page > 0)
                .build();
    }

    // ═══════════════════════════════════════════════════════════
    //  Ranking & Pagination
    // ═══════════════════════════════════════════════════════════

    private RecommendationResultDTO buildPaginatedResult(Map<UUID, PaperScore> scoredPapers,
                                                          int page, int size) {
        if (scoredPapers.isEmpty()) {
            return RecommendationResultDTO.builder()
                    .recommendations(List.of())
                    .totalElements(0L)
                    .totalPages(0)
                    .currentPage(page)
                    .pageSize(size)
                    .hasNext(false)
                    .hasPrev(false)
                    .build();
        }

        // Sort by score descending
        List<Map.Entry<UUID, PaperScore>> sorted = scoredPapers.entrySet().stream()
                .sorted(Map.Entry.<UUID, PaperScore>comparingByValue(
                        Comparator.comparingDouble(PaperScore::getScore).reversed()))
                .toList();

        long total = sorted.size();
        int totalPages = (int) Math.ceil((double) total / size);
        int start = page * size;
        int end = Math.min(start + size, sorted.size());

        if (start >= sorted.size()) {
            return RecommendationResultDTO.builder()
                    .recommendations(List.of())
                    .totalElements(total)
                    .totalPages(totalPages)
                    .currentPage(page)
                    .pageSize(size)
                    .hasNext(false)
                    .hasPrev(page > 0 && totalPages > 0)
                    .build();
        }

        // Batch-load papers with details
        List<UUID> pageIds = sorted.subList(start, end).stream()
                .map(Map.Entry::getKey)
                .toList();
        Map<UUID, ResearchPaper> paperMap = researchPaperRepository.findAllByIdWithDetails(pageIds)
                .stream().collect(Collectors.toMap(ResearchPaper::getPaperId, p -> p));
        Map<UUID, PaperScore> scoreMap = sorted.subList(start, end).stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        List<RecommendationResponseDTO> recommendations = pageIds.stream()
                .map(id -> {
                    ResearchPaper paper = paperMap.get(id);
                    PaperScore score = scoreMap.get(id);
                    if (paper == null || score == null) return null;
                    return RecommendationResponseDTO.builder()
                            .paper(mapToDetailDTO(paper))
                            .reason(RecommendationResponseDTO.Reason.CONTENT_BASED)
                            .reasonDetail(score.reason)
                            .build();
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return RecommendationResultDTO.builder()
                .recommendations(recommendations)
                .totalElements(total)
                .totalPages(totalPages)
                .currentPage(page)
                .pageSize(size)
                .hasNext(end < sorted.size())
                .hasPrev(page > 0)
                .build();
    }

    // ═══════════════════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════════════════

    private Set<UUID> getBookmarkedPaperIds(UUID userId) {
        return bookmarkRepository.findByUser_UserId(userId, PageRequest.of(0, 200))
                .stream()
                .filter(b -> b.getPaper() != null)
                .map(b -> b.getPaper().getPaperId())
                .collect(Collectors.toSet());
    }

    private boolean isSyntheticKeyword(PaperKeyword pk) {
        return pk.getRelevanceScore() != null && Math.abs(pk.getRelevanceScore() - 1.0) < 0.0001;
    }

    /**
     * Map a ResearchPaper entity to PaperDetailResponseDTO.
     * Mirrors the mapping pattern in {@link com.sra.journal_tracking.service.PaperSearchOrchestrator}.
     */
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

    // ═══════════════════════════════════════════════════════════
    //  Cache eviction (called when user activity changes)
    // ═══════════════════════════════════════════════════════════

    /**
     * Evict all cached recommendations for a user.
     * Called after a new search is recorded or a bookmark is added/removed.
     */
    public void evictUserCache(String userEmail) {
        String prefix = "rec:" + userEmail + ":";
        recCache.keySet().removeIf(key -> key.startsWith(prefix));
        log.debug("Evicted recommendation cache for {}", userEmail);
    }

    // ═══════════════════════════════════════════════════════════
    //  Inner class: scored paper accumulator
    // ═══════════════════════════════════════════════════════════

    private static class PaperScore implements Comparable<PaperScore> {
        private double score;
        private String reason;

        PaperScore(double score, String reason) {
            this.score = score;
            this.reason = reason;
        }

        double getScore() { return score; }

        static PaperScore merge(PaperScore existing, PaperScore incoming) {
            existing.score += incoming.score;
            // Keep the more specific reason
            if (existing.reason == null || existing.reason.startsWith("Matches")) {
                existing.reason = incoming.reason;
            }
            return existing;
        }

        @Override
        public int compareTo(PaperScore other) {
            return Double.compare(other.score, this.score); // descending
        }
    }
}
