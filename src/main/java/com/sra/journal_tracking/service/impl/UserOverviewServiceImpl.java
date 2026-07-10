package com.sra.journal_tracking.service.impl;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sra.journal_tracking.dto.author.AuthorResearchFocusResponse;
import com.sra.journal_tracking.dto.author.AuthorTimelineResponse;
import com.sra.journal_tracking.dto.follow.FollowResponse;
import com.sra.journal_tracking.dto.overview.UserOverviewResponse;
import com.sra.journal_tracking.dto.overview.UserOverviewResponse.CitationYearEntry;
import com.sra.journal_tracking.dto.overview.UserOverviewResponse.RecentPublicationEntry;
import com.sra.journal_tracking.dto.overview.UserOverviewResponse.ResearchFieldEntry;
import com.sra.journal_tracking.entity.jpa.User;
import com.sra.journal_tracking.entity.jpa.UserSearchHistory;
import com.sra.journal_tracking.entity.jpa.UserUsage;
import com.sra.journal_tracking.exception.AppException;
import com.sra.journal_tracking.exception.ErrorCode;
import com.sra.journal_tracking.repository.jpa.AuthorRepository;
import com.sra.journal_tracking.repository.jpa.BookmarkRepository;
import com.sra.journal_tracking.repository.jpa.FollowRepository;
import com.sra.journal_tracking.repository.jpa.KeywordRepository;
import com.sra.journal_tracking.repository.jpa.KeywordRepository;
import com.sra.journal_tracking.repository.jpa.ResearchPaperRepository;
import com.sra.journal_tracking.repository.jpa.SystemConfigRepository;
import com.sra.journal_tracking.repository.jpa.UserRepository;
import com.sra.journal_tracking.repository.jpa.UserSearchHistoryRepository;
import com.sra.journal_tracking.repository.jpa.UserUsageRepository;
import com.sra.journal_tracking.service.AuthorQuickStatsService;
import com.sra.journal_tracking.service.DataSyncService;
import com.sra.journal_tracking.service.UserOverviewService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserOverviewServiceImpl implements UserOverviewService {

    private final UserRepository userRepository;
    private final UserUsageRepository userUsageRepository;
    private final UserSearchHistoryRepository searchHistoryRepository;
    private final KeywordRepository keywordRepository;
    private final SystemConfigRepository systemConfigRepository;
    private final ResearchPaperRepository researchPaperRepository;
    private final AuthorRepository authorRepository;
    private final FollowRepository followRepository;
    private final BookmarkRepository bookmarkRepository;
    private final DataSyncService dataSyncService;
    private final AuthorQuickStatsService authorQuickStatsService;

    @Override
    @Cacheable(value = "overview:user", cacheManager = "defaultCacheManager",
               key = "#userEmail + '_' + (#authorId != null ? #authorId.toString() : 'none')",
               unless = "#result == null")
    public UserOverviewResponse getUserOverview(String userEmail, UUID authorId) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found: " + userEmail));

        String userRole = user.getRole().getRoleName();
        String currentMonth = YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));

        // Card 1: Total papers in the system
        long totalPapers = researchPaperRepository.count();

        // Card 2: Papers viewed this month
        long papersViewed = userUsageRepository
                .findByUser_UserIdAndUsageMonth(user.getUserId(), currentMonth)
                .map(UserUsage::getViewCount)
                .map(Long::valueOf)
                .orElse(0L);

        // Card 3: Searches remaining
        Integer searchesRemaining = null;
        Integer monthlySearchLimit = null;

        if ("ACADEMIC_USER".equalsIgnoreCase(userRole)) {
            int limit = systemConfigRepository.findByConfigKey("academic_monthly_search_limit")
                    .map(cfg -> Integer.parseInt(cfg.getConfigValue()))
                    .orElse(30);
            int usedSearches = userUsageRepository
                    .findByUser_UserIdAndUsageMonth(user.getUserId(), currentMonth)
                    .map(UserUsage::getSearchCount)
                    .orElse(0);
            searchesRemaining = Math.max(0, limit - usedSearches);
            monthlySearchLimit = limit;
        }

        // Card 4: Total keywords
        long totalKeywords = keywordRepository.count();

        // ── Activity Summary ──
        long bookmarksThisMonth = bookmarkRepository.countByUserAndCreatedBetween(
                user.getUserId(),
                YearMonth.now().atDay(1).atStartOfDay(),
                YearMonth.now().plusMonths(1).atDay(1).atStartOfDay());
        int searchesThisMonth = userUsageRepository
                .findByUser_UserIdAndUsageMonth(user.getUserId(), currentMonth)
                .map(UserUsage::getSearchCount)
                .orElse(0);

        // ── Research Fields from user's search history ──
        List<ResearchFieldEntry> researchFields = buildResearchFieldsFromHistory(user.getUserId());

        // ── Researcher-specific features (only when authorId is provided) ──
        Integer hIndex = null;
        List<CitationYearEntry> citationHistory = Collections.emptyList();
        List<RecentPublicationEntry> recentPublications = Collections.emptyList();

        if (authorId != null) {
            var authorOpt = authorRepository.findById(authorId);
            if (authorOpt.isPresent()) {
                String authorName = authorOpt.get().getFullName();

                try {
                    // Fetch citation history from OpenAlex (counts_by_year)
                    AuthorTimelineResponse timeline = authorQuickStatsService.getTimeline(authorName);
                    hIndex = timeline.getHIndex();
                    citationHistory = timeline.getTimeline().stream()
                            .map(p -> CitationYearEntry.builder()
                                    .y(p.getYear())
                                    .citations(p.getCitedByCount())
                                    .build())
                            .collect(Collectors.toList());

                } catch (AppException e) {
                    log.warn("OpenAlex error for '{}': {}", authorName, e.getMessage());
                } catch (Exception e) {
                    log.warn("Unexpected error for '{}': {}", authorName, e.getMessage());
                }

                // Fetch recent publications from local DB
                try {
                    List<Object[]> recentPublicationRows = researchPaperRepository
                            .getAuthorRecentPublications(authorName, 10);
                    recentPublications = buildRecentPublications(recentPublicationRows);
                } catch (Exception e) {
                    log.warn("Failed to load recent publications for '{}': {}", authorName, e.getMessage());
                }
            }
        }

        log.info("User overview for {} ({}): totalPapers={}, papersViewed={}, searchesRemaining={}, "
                + "totalKeywords={}, hIndex={}, citationYears={}, researchFields={}, recentPubs={}",
                userEmail, userRole, totalPapers, papersViewed, searchesRemaining,
                totalKeywords, hIndex,
                citationHistory.size(), researchFields.size(), recentPublications.size());

        return UserOverviewResponse.builder()
                .totalPapers(totalPapers)
                .papersViewed(papersViewed)
                .searchesRemaining(searchesRemaining)
                .monthlySearchLimit(monthlySearchLimit)
                .totalKeywords(totalKeywords)
                .bookmarksThisMonth(bookmarksThisMonth)
                .searchesThisMonth(searchesThisMonth)
                .hIndex(hIndex)
                .citationHistory(citationHistory)
                .researchFields(researchFields)
                .recentPublications(recentPublications)
                .build();
    }

    // ── Private helpers ──

    /**
     * Compute h-index from a list of papers sorted by citationCount DESC.
     * h-index = max H such that H papers have at least H citations each.
     */
    private Integer computeHIndex(List<Object[]> papersSortedByCitationsDesc) {
        int h = 0;
        for (int i = 0; i < papersSortedByCitationsDesc.size(); i++) {
            int citations = ((Number) papersSortedByCitationsDesc.get(i)[2]).intValue();
            if (citations >= i + 1) {
                h = i + 1;
            } else {
                break; // remaining papers have fewer citations, can't increase h
            }
        }
        return h;
    }

    /**
     * Build citation history grouped by publication year (ascending).
     */
    private List<CitationYearEntry> buildCitationHistory(List<Object[]> papersWithCitations) {
        Map<Integer, Integer> yearToCitations = new LinkedHashMap<>();
        for (Object[] row : papersWithCitations) {
            Short pubYear = (Short) row[1];
            int citations = ((Number) row[2]).intValue();
            if (pubYear != null) {
                yearToCitations.merge((int) pubYear, citations, Integer::sum);
            }
        }
        return yearToCitations.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> CitationYearEntry.builder()
                        .y(e.getKey())
                        .citations(e.getValue())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Build research fields from user's search history keywords.
     * Aggregates last 50 KEYWORD searches, calculates percentages, returns top 8.
     */
    private List<ResearchFieldEntry> buildResearchFieldsFromHistory(UUID userId) {
        try {
            List<UserSearchHistory> searches = searchHistoryRepository
                    .findByUser_UserIdOrderBySearchedAtDesc(userId, PageRequest.of(0, 50));

            Map<String, Long> keywordCounts = new LinkedHashMap<>();
            for (UserSearchHistory s : searches) {
                if (!"KEYWORD".equals(s.getSearchType())) continue;
                String kw = s.getSearchText().toLowerCase().trim();
                if (kw.isEmpty() || kw.length() < 2) continue;
                keywordCounts.merge(kw, 1L, Long::sum);
            }

            if (keywordCounts.isEmpty()) return List.of();

            long total = keywordCounts.values().stream().mapToLong(Long::longValue).sum();
            if (total == 0) return List.of();

            return keywordCounts.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(8)
                    .map(e -> {
                        double pct = Math.round((e.getValue() * 100.0 / total) * 10.0) / 10.0;
                        return ResearchFieldEntry.builder()
                                .name(e.getKey())
                                .value(pct)
                                .build();
                    })
                    .toList();
        } catch (Exception e) {
            log.warn("Failed to build research fields from history: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Build research fields breakdown as percentages (top 8 keywords).
     */
    private List<ResearchFieldEntry> buildResearchFields(List<Object[]> keywordDistribution) {
        long total = keywordDistribution.stream()
                .mapToLong(r -> ((Number) r[1]).longValue())
                .sum();
        if (total == 0) return Collections.emptyList();

        return keywordDistribution.stream()
                .limit(8) // top 8
                .map(r -> {
                    String keyword = (String) r[0];
                    long count = ((Number) r[1]).longValue();
                    double pct = Math.round((count * 100.0 / total) * 10.0) / 10.0; // 1 decimal
                    return ResearchFieldEntry.builder()
                            .name(keyword)
                            .value(pct)
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * Build recent publications list with author role detection.
     */
    private List<RecentPublicationEntry> buildRecentPublications(List<Object[]> rows) {
        return rows.stream()
                .map(r -> {
                    String paperId = r[0].toString();
                    String title = (String) r[1];
                    String journalName = (String) r[2];
                    int pubYear = r[3] != null ? ((Number) r[3]).intValue() : 0;
                    int authorOrder = ((Number) r[4]).intValue();
                    int citations = ((Number) r[5]).intValue();
                    boolean isCorresponding = r[6] != null && (Boolean) r[6];

                    String role;
                    if (authorOrder == 1) {
                        role = "First Author";
                    } else if (isCorresponding) {
                        role = "Corresponding Author";
                    } else {
                        role = "Co-Author";
                    }

                    return RecentPublicationEntry.builder()
                            .paperId(paperId.toString())
                            .title(title)
                            .journal(journalName)
                            .year(pubYear)
                            .role(role)
                            .citations(citations)
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<FollowResponse> getFollowedAuthors(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found: " + userEmail));

        return followRepository.findByUser_UserId(user.getUserId())
                .stream()
                .filter(f -> f.getAuthor() != null)
                .map(f -> FollowResponse.builder()
                        .followId(f.getFollowId())
                        .authorId(f.getAuthor().getAuthorId())
                        .authorName(f.getAuthor().getFullName())
                        .notifyEnabled(f.getNotifyEnabled())
                        .createdAt(f.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }
}
