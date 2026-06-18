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
import com.sra.journal_tracking.service.DataSyncService;
import com.sra.journal_tracking.service.PaperSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PaperSearchServiceImpl implements PaperSearchService {

    private final ResearchPaperRepository researchPaperRepository;
    private final UserRepository userRepository;
    private final UserUsageRepository userUsageRepository;
    private final SystemConfigRepository systemConfigRepository;
    private final DataSyncService dataSyncService;

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

        // Phase 1: Fast ID query — SQL pagination thực sự, không FETCH JOIN
        // Keyword-only search → dùng query đơn giản (chỉ Title + Abstract, 0 JOIN, có cache)
        // Có author/journal filter → dùng query đầy đủ (JOIN 5 bảng)
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<UUID> idPage;
        if (authorName == null && journalId == null) {
            idPage = researchPaperRepository.searchPaperIdsByKeyword(query, pageable);
        } else {
            idPage = researchPaperRepository.searchPaperIdsWithFilters(query, authorName, journalId, pageable);
        }

        // Fallback: nếu SQL không có kết quả → sync trực tiếp từ OpenAlex
        if (idPage.getTotalElements() == 0 && authorName == null && journalId == null) {
            log.info("No SQL results for '{}', syncing directly from OpenAlex", query);
            List<ResearchPaper> syncedPapers = dataSyncService.syncFromOpenAlexAndReturnPapers(query, size);
            if (!syncedPapers.isEmpty()) {
                return mapToSearchResultDTOFromList(syncedPapers);
            }
        }

        // Phase 2: Load full details chỉ cho N papers của trang hiện tại (có JOIN FETCH)
        List<ResearchPaper> papers = fetchPapersInOrder(idPage.getContent());

        return buildSearchResult(papers, idPage);
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

        // Phase 1: Fast ID query
        Page<UUID> idPage = researchPaperRepository.searchAuthorPaperIds(authorName.trim(), pageable);

        // Phase 2: Load full details
        List<ResearchPaper> papers = fetchPapersInOrder(idPage.getContent());

        return buildSearchResult(papers, idPage);
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

        // Phase 1: Fast ID query
        Page<UUID> idPage = researchPaperRepository.findPaperIdsByJournalId(journalId, pageable);

        // Phase 2: Load full details
        List<ResearchPaper> papers = fetchPapersInOrder(idPage.getContent());

        return buildSearchResult(papers, idPage);
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

        Short pubYearFrom = filterRequest.getPubYearFrom();
        Short pubYearTo = filterRequest.getPubYearTo();
        if (pubYearFrom != null && pubYearTo != null && pubYearFrom > pubYearTo) {
            throw new IllegalArgumentException("pubYearFrom must be <= pubYearTo");
        }

        int page = Math.max(0, filterRequest.getPage());
        int size = Math.min(50, Math.max(1, filterRequest.getSize()));
        Pageable pageable = PageRequest.of(page, size);

        // Phase 1: Fast ID query
        Page<UUID> idPage = researchPaperRepository.advancedFilterIds(
                pubYearFrom,
                pubYearTo,
                filterRequest.getFieldId() != null ? UUID.fromString(filterRequest.getFieldId()) : null,
                filterRequest.getJournalId() != null ? UUID.fromString(filterRequest.getJournalId()) : null,
                filterRequest.getIsOpenAccess(),
                filterRequest.getMinCitations(),
                pageable
        );

        // Phase 2: Load full details
        List<ResearchPaper> papers = fetchPapersInOrder(idPage.getContent());

        return buildSearchResult(papers, idPage);
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

    /**
     * Load full paper details (với authors, keywords, journal) theo đúng thứ tự ID.
     * Chỉ gọi cho N papers của trang hiện tại → rất nhanh.
     */
    private List<ResearchPaper> fetchPapersInOrder(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<ResearchPaper> papers = researchPaperRepository.findByIdsWithDetails(ids);
        Map<UUID, ResearchPaper> paperMap = papers.stream()
                .collect(Collectors.toMap(ResearchPaper::getPaperId, Function.identity()));
        return ids.stream()
                .map(paperMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private PaperSearchResultDTO buildSearchResult(List<ResearchPaper> papers, Page<UUID> idPage) {
        List<PaperDetailResponseDTO> dtos = papers.stream()
                .map(this::mapToDetailDTO)
                .collect(Collectors.toList());

        return PaperSearchResultDTO.builder()
                .papers(dtos)
                .totalElements(idPage.getTotalElements())
                .totalPages(idPage.getTotalPages())
                .currentPage(idPage.getNumber())
                .pageSize(idPage.getSize())
                .hasNext(idPage.hasNext())
                .hasPrev(idPage.hasPrevious())
                .build();
    }

    /**
     * Map a plain List of ResearchPaper to search result DTO (used after OpenAlex sync).
     * Papers are already sorted by createdAt DESC from the sync order (newest first).
     */
    private PaperSearchResultDTO mapToSearchResultDTOFromList(List<ResearchPaper> papers) {
        List<PaperDetailResponseDTO> dtos = papers.stream()
                .map(this::mapToDetailDTO)
                .collect(Collectors.toList());

        return PaperSearchResultDTO.builder()
                .papers(dtos)
                .totalElements((long) dtos.size())
                .totalPages(dtos.isEmpty() ? 0 : 1)
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
                .map(pk -> KeywordDTO.builder()
                        .keywordText(pk.getKeyword().getKeywordText())
                        .relevanceScore(pk.getRelevanceScore())
                        .build())
                .collect(Collectors.toList()) : new ArrayList<>();

        String sourceUrl = paper.getDoi() != null ? "https://doi.org/" + paper.getDoi() : null;
        Boolean pdfAvailable = Boolean.TRUE.equals(paper.getIsOpenAccess()) || paper.getDoi() != null;

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
                .downloadUrl(sourceUrl)
                .rating(0.0)
                .downloadCount(0)
                .commentCount(0)
                .createdAt(paper.getCreatedAt())
                .build();
    }
}
