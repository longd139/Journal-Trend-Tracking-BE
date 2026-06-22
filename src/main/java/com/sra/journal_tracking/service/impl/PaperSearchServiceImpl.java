package com.sra.journal_tracking.service.impl;

import com.sra.journal_tracking.dto.paper.*;
import com.sra.journal_tracking.entity.jpa.*;
import com.sra.journal_tracking.exception.PaperNotFoundException;
import com.sra.journal_tracking.exception.UnauthorizedAccessException;
import com.sra.journal_tracking.exception.UsageLimitExceededException;
import com.sra.journal_tracking.repository.jpa.ResearchPaperRepository;
import com.sra.journal_tracking.repository.jpa.SystemConfigRepository;
import com.sra.journal_tracking.repository.jpa.UserRepository;
import com.sra.journal_tracking.repository.jpa.UserUsageRepository;
import com.sra.journal_tracking.service.KeywordExpansionService;
import com.sra.journal_tracking.service.OpenAlexFallbackSearchService;
import com.sra.journal_tracking.service.PaperSearchService;
import com.sra.journal_tracking.service.SearchBackfillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PaperSearchServiceImpl implements PaperSearchService {
    private static final int RECENT_PUBLICATION_YEAR_WINDOW = 3;
    private static final double MIN_PRIMARY_KEYWORD_SCORE = 0.55d;
    private static final double MIN_RELEVANCE_SCORE = 8.0d;

    private final ResearchPaperRepository researchPaperRepository;
    private final UserRepository userRepository;
    private final UserUsageRepository userUsageRepository;
    private final SystemConfigRepository systemConfigRepository;
    private final SearchBackfillService searchBackfillService;
    private final KeywordExpansionService keywordExpansionService;
    private final OpenAlexFallbackSearchService openAlexFallbackSearchService;

    @Override
    public PaperSearchResultDTO searchPapers(PaperSearchRequestDTO request, String userEmail) {
        log.info("Search papers: query={}, user={}", request.getQuery(), userEmail);

        String query = request.getQuery() != null ? request.getQuery().trim() : "";
        if (query.isEmpty()) {
            throw new IllegalArgumentException("Search query cannot be empty");
        }
        String authorName = request.getAuthorName() != null && !request.getAuthorName().trim().isEmpty()
                ? request.getAuthorName().trim() : null;

        UUID journalId = null;
        if (request.getJournalId() != null && !request.getJournalId().trim().isEmpty()) {
            try {
                journalId = UUID.fromString(request.getJournalId().trim());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid journal ID format");
            }
        }

        int page = Math.max(0, request.getPage());
        int size = Math.min(50, Math.max(1, request.getSize()));

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found: " + userEmail));

        if ("ACADEMIC_USER".equalsIgnoreCase(user.getRole().getRoleName())) {
            checkAndIncrementUsage(user.getUserId(), "search");
        }

        short startYear = recentStartYear();
        short endYear = currentYear();
        List<String> expandedTerms = keywordExpansionService.expand(query, 6);
        RankedSearchResult rankedResults = loadRankedSearchResults(
                query,
                expandedTerms,
                authorName,
                journalId,
                startYear,
                endYear,
                page,
                size);

        if (rankedResults.totalElements() == 0 && authorName == null && journalId == null) {
            log.info("No local results for '{}'; searching OpenAlex fallback", query);
            if (page == 0) {
                List<PaperDetailResponseDTO> fallbackPapers = openAlexFallbackSearchService.search(query, size);
                searchBackfillService.requestBackfill(query, size);
                if (!fallbackPapers.isEmpty()) {
                    return mapFallbackSearchResultDTO(fallbackPapers, page, size);
                }
            } else {
                searchBackfillService.requestBackfill(query, size);
            }
        }

        return mapToSearchResultDTO(rankedResults.papers(), page, size, rankedResults.totalElements());
    }

    @Override
    public PaperSearchResultDTO searchByAuthor(PaperSearchRequestDTO request, String userEmail) {
        String authorName = request.getAuthorName();
        if (authorName == null || authorName.trim().isEmpty()) {
            throw new IllegalArgumentException("Author name cannot be empty");
        }

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found: " + userEmail));

        if ("ACADEMIC_USER".equalsIgnoreCase(user.getRole().getRoleName())) {
            checkAndIncrementUsage(user.getUserId(), "search");
        }

        int page = Math.max(0, request.getPage());
        int size = Math.min(50, Math.max(1, request.getSize()));
        Pageable pageable = PageRequest.of(page, size);

        Page<ResearchPaper> results = researchPaperRepository.searchByAuthorName(
                authorName,
                recentStartYear(),
                currentYear(),
                pageable);

        return mapToSearchResultDTO(results);
    }

    @Override
    public PaperSearchResultDTO searchByJournal(PaperSearchRequestDTO request, String userEmail) {
        String journalIdStr = request.getJournalId();
        if (journalIdStr == null || journalIdStr.trim().isEmpty()) {
            throw new IllegalArgumentException("Journal ID cannot be empty");
        }

        UUID journalId = UUID.fromString(journalIdStr);

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found: " + userEmail));

        if ("ACADEMIC_USER".equalsIgnoreCase(user.getRole().getRoleName())) {
            checkAndIncrementUsage(user.getUserId(), "search");
        }

        int page = Math.max(0, request.getPage());
        int size = Math.min(50, Math.max(1, request.getSize()));
        Pageable pageable = PageRequest.of(page, size);

        Page<ResearchPaper> results = researchPaperRepository.findByJournal_JournalIdAndPubYearBetween(
                journalId,
                recentStartYear(),
                currentYear(),
                pageable);

        return mapToSearchResultDTO(results);
    }

    @Override
    public PaperSearchResultDTO advancedFilter(PaperAdvancedFilterRequestDTO filterRequest, String userEmail) {
        log.info("Advanced filter: user={}", userEmail);

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found: " + userEmail));
        
        String roleName = user.getRole().getRoleName();
        if (!"RESEARCHER".equalsIgnoreCase(roleName) && !"ADMIN".equalsIgnoreCase(roleName)) {
            throw new UnauthorizedAccessException("Advanced filtering is available for Researcher or Admin users only");
        }

        Short pubYearFrom = maxYear(filterRequest.getPubYearFrom(), recentStartYear());
        Short pubYearTo = minYear(filterRequest.getPubYearTo(), currentYear());
        if (pubYearFrom != null && pubYearTo != null && pubYearFrom > pubYearTo) {
            throw new IllegalArgumentException("pubYearFrom must be <= pubYearTo");
        }

        int page = Math.max(0, filterRequest.getPage());
        int size = Math.min(50, Math.max(1, filterRequest.getSize()));
        Pageable pageable = PageRequest.of(page, size);

        Page<ResearchPaper> results = researchPaperRepository.advancedFilter(
                pubYearFrom,
                pubYearTo,
                filterRequest.getFieldId() != null ? UUID.fromString(filterRequest.getFieldId()) : null,
                filterRequest.getJournalId() != null ? UUID.fromString(filterRequest.getJournalId()) : null,
                filterRequest.getIsOpenAccess(),
                filterRequest.getMinCitations(),
                pageable
        );

        return mapToSearchResultDTO(results);
    }

    @Override
    public PaperDetailResponseDTO getPaperDetails(UUID paperId, String userEmail) {
        log.info("Get paper details: paperId={}, user={}", paperId, userEmail);

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found: " + userEmail));

        if ("ACADEMIC_USER".equalsIgnoreCase(user.getRole().getRoleName())) {
            checkAndIncrementUsage(user.getUserId(), "view");
        }

        ResearchPaper paper = researchPaperRepository.findByIdWithDetails(paperId)
                .orElseThrow(() -> new PaperNotFoundException("Paper not found with ID: " + paperId));

        return mapToDetailDTO(paper);
    }

    @Override
    public void checkAndIncrementUsage(UUID userId, String usageType) {
        String currentMonth = YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        log.info("Checking usage: userId={}, type={}, month={}", userId, usageType, currentMonth);

        UserUsage usage = userUsageRepository.findByUser_UserIdAndUsageMonth(userId, currentMonth)
                .orElseGet(() -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new RuntimeException("User not found: " + userId));
                    UserUsage newUsage = UserUsage.builder()
                            .user(user)
                            .usageMonth(currentMonth)
                            .searchCount(0)
                            .viewCount(0)
                            .chartViewCount(0)
                            .build();
                    return userUsageRepository.save(newUsage);
                });

        int limit = getLimit(usageType);
        int currentCount = "search".equals(usageType) ? usage.getSearchCount() : usage.getViewCount();

        if (currentCount >= limit) {
            // TODO: Generate upgrade_prompt notification if hit limit
            throw new UsageLimitExceededException(
                    String.format("You have reached your monthly %s limit (%d). Upgrade to Researcher?", usageType, limit)
            );
        }

        if ("search".equals(usageType)) {
            userUsageRepository.incrementSearchCount(userId, currentMonth);
        } else if ("view".equals(usageType)) {
            userUsageRepository.incrementViewCount(userId, currentMonth);
        }

        if (currentCount + 1 >= limit * 0.8) {
            log.info("Usage at 80%: userId={}, type={}", userId, usageType);
            // Could send a warning notification here
        }
    }

    @Override
    public UsageLimitResponseDTO getRemainingUsage(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found: " + userEmail));

        String userRole = user.getRole().getRoleName();
        String currentMonth = YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));

        if ("RESEARCHER".equalsIgnoreCase(userRole)) {
            return UsageLimitResponseDTO.builder()
                    .remainingSearches(null)
                    .remainingViews(null)
                    .monthlyLimit(null)
                    .userRole("RESEARCHER")
                    .currentMonth(currentMonth)
                    .resetDate(LocalDate.now().plusMonths(1).withDayOfMonth(1).toString())
                    .build();
        }

        UserUsage usage = userUsageRepository.findByUser_UserIdAndUsageMonth(user.getUserId(), currentMonth)
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

        int searchLimit = getLimit("search");
        int viewLimit = getLimit("view");

        return UsageLimitResponseDTO.builder()
                .remainingSearches(Math.max(0, searchLimit - usage.getSearchCount()))
                .remainingViews(Math.max(0, viewLimit - usage.getViewCount()))
                .monthlyLimit(searchLimit)
                .userRole("ACADEMIC_USER")
                .currentMonth(currentMonth)
                .resetDate(LocalDate.now().plusMonths(1).withDayOfMonth(1).toString())
                .build();
    }

    private int getLimit(String usageType) {
        String configKey = "search".equals(usageType) ? "academic_monthly_search_limit" : "academic_monthly_view_limit";
        return systemConfigRepository.findByConfigKey(configKey)
                .map(config -> Integer.parseInt(config.getConfigValue()))
                .orElse("search".equals(usageType) ? 30 : 20);
    }

    private short currentYear() {
        return (short) Year.now().getValue();
    }

    private short recentStartYear() {
        return (short) (currentYear() - RECENT_PUBLICATION_YEAR_WINDOW + 1);
    }

    private Short maxYear(Short requestedYear, short minimumYear) {
        if (requestedYear == null || requestedYear < minimumYear) {
            return minimumYear;
        }
        return requestedYear;
    }

    private Short minYear(Short requestedYear, short maximumYear) {
        if (requestedYear == null || requestedYear > maximumYear) {
            return maximumYear;
        }
        return requestedYear;
    }

    private RankedSearchResult loadRankedSearchResults(
            String query,
            List<String> expandedTerms,
            String authorName,
            UUID journalId,
            short startYear,
            short endYear,
            int page,
            int size) {

        Map<UUID, ResearchPaper> candidates = new LinkedHashMap<>();
        List<String> localSearchTerms = selectLocalSearchTerms(query, expandedTerms);
        int candidatePageSize = candidatePageSize(query, page, size);

        for (String term : localSearchTerms) {
            findCandidatePapers(term, authorName, journalId, startYear, endYear, PageRequest.of(0, candidatePageSize))
                    .forEach(paper -> candidates.putIfAbsent(paper.getPaperId(), paper));
        }

        List<ScoredPaper> scoredPapers = candidates.values().stream()
                .map(paper -> new ScoredPaper(paper, calculateRelevanceScore(paper, query, expandedTerms)))
                .filter(scored -> scored.score() >= MIN_RELEVANCE_SCORE)
                .sorted(Comparator.comparingDouble(ScoredPaper::score).reversed())
                .collect(Collectors.toList());

        int offset = Math.max(0, page * size);
        List<ResearchPaper> pagePapers = scoredPapers.stream()
                .skip(offset)
                .limit(size)
                .map(ScoredPaper::paper)
                .collect(Collectors.toList());

        return new RankedSearchResult(pagePapers, scoredPapers.size());
    }

    private int candidatePageSize(String query, int page, int size) {
        int multiplier = extractSearchTokens(query).size() > 1 ? 2 : 3;
        int upperBound = extractSearchTokens(query).size() > 1 ? 50 : 100;
        return Math.min(Math.max((page + 1) * size * multiplier, 15), upperBound);
    }

    private List<String> selectLocalSearchTerms(String query, List<String> expandedTerms) {
        List<String> queryTokens = extractSearchTokens(query);
        if (queryTokens.size() > 1) {
            return List.of(query.trim());
        }

        return expandedTerms.stream()
                .limit(3)
                .collect(Collectors.toList());
    }

    private List<ResearchPaper> findCandidatePapers(
            String term,
            String authorName,
            UUID journalId,
            short startYear,
            short endYear,
            Pageable pageable) {

        if (authorName == null && journalId == null) {
            return researchPaperRepository.findPrimaryCandidatesWithoutFilters(term, startYear, endYear, pageable);
        }

        return researchPaperRepository.findPrimaryCandidates(
                term,
                authorName,
                journalId,
                startYear,
                endYear,
                pageable);
    }

    private double calculateRelevanceScore(ResearchPaper paper, String query, List<String> expandedTerms) {
        String normalizedQuery = normalizeSearchText(query);
        String primaryText = buildPrimarySearchableText(paper, normalizedQuery);
        String fullText = buildFullSearchableText(paper, normalizedQuery);
        List<String> queryTokens = extractSearchTokens(query);
        boolean exactPrimaryMatch = !normalizedQuery.isBlank() && containsNormalizedTerm(primaryText, normalizedQuery);
        boolean exactFullMatch = !normalizedQuery.isBlank() && containsNormalizedTerm(fullText, normalizedQuery);

        if (queryTokens.size() > 1 && !exactPrimaryMatch && !exactFullMatch && !allTokensMatch(fullText, queryTokens)) {
            return 0.0d;
        }

        double score = 0.0d;
        if (!normalizedQuery.isBlank()) {
            if (exactPrimaryMatch) {
                score += 20.0d;
            } else if (exactFullMatch) {
                score += 4.0d;
            }
        }

        for (String token : queryTokens) {
            if (containsTokenVariant(primaryText, token)) {
                score += 8.0d;
            } else if (containsTokenVariant(fullText, token)) {
                score += 2.0d;
            }
        }

        for (String term : expandedTerms) {
            String normalizedTerm = normalizeSearchText(term);
            if (normalizedTerm.isBlank() || normalizedTerm.equals(normalizedQuery) || queryTokens.contains(normalizedTerm)) {
                continue;
            }
            if (containsNormalizedTerm(primaryText, normalizedTerm)) {
                score += 5.0d;
            } else if (containsNormalizedTerm(fullText, normalizedTerm)) {
                score += 1.0d;
            }
        }

        return score;
    }

    private boolean allTokensMatch(String text, List<String> tokens) {
        return tokens.stream().allMatch(token -> containsTokenVariant(text, token));
    }

    private String buildPrimarySearchableText(ResearchPaper paper, String normalizedQuery) {
        StringBuilder text = new StringBuilder();
        append(text, paper.getTitle());
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
        if (paper.getJournal() != null && !isInstitutionNoiseQuery(normalizedQuery)) {
            append(text, paper.getJournal().getJournalName());
        }
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

    private boolean isInstitutionNoiseQuery(String normalizedQuery) {
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

    private PaperSearchResultDTO mapToSearchResultDTO(List<ResearchPaper> papers, int page, int size, long totalElements) {
        List<PaperDetailResponseDTO> dtos = papers.stream()
                .map(this::mapToDetailDTO)
                .collect(Collectors.toList());

        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        return PaperSearchResultDTO.builder()
                .papers(dtos)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .currentPage(page)
                .pageSize(size)
                .hasNext(page + 1 < totalPages)
                .hasPrev(page > 0)
                .build();
    }

    private PaperSearchResultDTO mapFallbackSearchResultDTO(List<PaperDetailResponseDTO> papers, int page, int size) {
        long totalElements = papers.size();
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        return PaperSearchResultDTO.builder()
                .papers(papers)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .currentPage(page)
                .pageSize(size)
                .hasNext(false)
                .hasPrev(page > 0)
                .build();
    }

    private PaperSearchResultDTO mapToSearchResultDTO(Page<ResearchPaper> paperPage) {
        List<PaperDetailResponseDTO> dtos = paperPage.getContent().stream()
                .map(this::mapToDetailDTO)
                .collect(Collectors.toList());

        return PaperSearchResultDTO.builder()
                .papers(dtos)
                .totalElements(paperPage.getTotalElements())
                .totalPages(paperPage.getTotalPages())
                .currentPage(paperPage.getNumber())
                .pageSize(paperPage.getSize())
                .hasNext(paperPage.hasNext())
                .hasPrev(paperPage.hasPrevious())
                .build();
    }

    private PaperDetailResponseDTO mapToDetailDTO(ResearchPaper paper) {
        List<AuthorDTO> authors = paper.getAuthors() != null ? paper.getAuthors().stream()
                .sorted(Comparator.comparing(pa -> pa.getAuthorOrder() != null ? pa.getAuthorOrder() : Integer.MAX_VALUE))
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
                .sorted(Comparator.comparing(
                        pk -> pk.getRelevanceScore() != null ? pk.getRelevanceScore() : 0.0d,
                        Comparator.reverseOrder()))
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

    private boolean isSyntheticKeyword(PaperKeyword paperKeyword) {
        return paperKeyword.getRelevanceScore() != null
                && Double.compare(paperKeyword.getRelevanceScore(), 1.0d) == 0;
    }

    private boolean isPrimaryKeyword(PaperKeyword paperKeyword) {
        return !isSyntheticKeyword(paperKeyword)
                && paperKeyword.getRelevanceScore() != null
                && paperKeyword.getRelevanceScore() >= MIN_PRIMARY_KEYWORD_SCORE;
    }

    private record ScoredPaper(ResearchPaper paper, double score) {
    }

    private record RankedSearchResult(List<ResearchPaper> papers, long totalElements) {
    }
}
