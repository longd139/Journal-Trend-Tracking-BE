package com.sra.journal_tracking.controller;

import com.sra.journal_tracking.constants.KeywordConstants;
import com.sra.journal_tracking.dto.paper.KeywordDTO;
import com.sra.journal_tracking.dto.paper.PaperAdvancedFilterRequestDTO;
import com.sra.journal_tracking.dto.paper.PaperDetailResponseDTO;
import com.sra.journal_tracking.dto.paper.PaperSearchRequestDTO;
import com.sra.journal_tracking.dto.paper.PaperSearchResultDTO;
import com.sra.journal_tracking.dto.paper.SearchQuotaResponseDTO;
import com.sra.journal_tracking.dto.paper.UsageLimitResponseDTO;
import com.sra.journal_tracking.dto.response.AppResponse;
import com.sra.journal_tracking.entity.jpa.ResearchPaper;
import com.sra.journal_tracking.repository.jpa.ResearchPaperRepository;
import com.sra.journal_tracking.service.CitationService;
import com.sra.journal_tracking.service.OpenAlexFallbackSearchService;
import com.sra.journal_tracking.service.PaperSearchOrchestrator;
import com.sra.journal_tracking.service.PaperSearchService;
import com.sra.journal_tracking.service.RatingCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import jakarta.validation.Valid;
import java.time.Year;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/papers")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
public class PaperSearchController {
    private static final int RECENT_PUBLICATION_YEAR_WINDOW = 3;

    private final PaperSearchService paperSearchService;
    private final PaperSearchOrchestrator paperSearchOrchestrator;
    private final OpenAlexFallbackSearchService openAlexFallbackSearchService;
    private final ResearchPaperRepository researchPaperRepository;
    private final CitationService citationService;

    @Operation(summary = "Browse all papers", description = "Get all papers in database with pagination. No search required.")
    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<AppResponse<PaperSearchResultDTO>> browsePapers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(50, Math.max(1, size));
        short endYear = (short) Year.now().getValue();
        short startYear = (short) (endYear - RECENT_PUBLICATION_YEAR_WINDOW + 1);
        Page<ResearchPaper> paperPage = researchPaperRepository.findByPubYearBetween(
                startYear,
                endYear,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt")));

        List<PaperDetailResponseDTO> dtos = paperPage.getContent().stream()
                .map(this::toSummaryDTO)
                .collect(Collectors.toList());

        PaperSearchResultDTO result = PaperSearchResultDTO.builder()
                .papers(dtos)
                .totalElements(paperPage.getTotalElements())
                .totalPages(paperPage.getTotalPages())
                .currentPage(paperPage.getNumber())
                .pageSize(paperPage.getSize())
                .hasNext(paperPage.hasNext())
                .hasPrev(paperPage.hasPrevious())
                .build();

        return ResponseEntity.ok(AppResponse.success("Papers retrieved", result));
    }

    @Operation(summary = "Browse all papers with sorting", description = "Get all papers with user-controlled sorting.")
    @GetMapping("/sorted")
    @Transactional(readOnly = true)
    public ResponseEntity<AppResponse<PaperSearchResultDTO>> browsePapersSorted(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "date") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDirection) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(50, Math.max(1, size));
        short endYear = (short) Year.now().getValue();
        short startYear = (short) (endYear - RECENT_PUBLICATION_YEAR_WINDOW + 1);
        Sort sort = buildBrowseSort(sortBy, sortDirection);
        Page<ResearchPaper> paperPage = researchPaperRepository.findByPubYearBetween(
                startYear,
                endYear,
                PageRequest.of(safePage, safeSize, sort));

        List<PaperDetailResponseDTO> dtos = paperPage.getContent().stream()
                .map(this::toSummaryDTO)
                .collect(Collectors.toList());

        PaperSearchResultDTO result = PaperSearchResultDTO.builder()
                .papers(dtos)
                .totalElements(paperPage.getTotalElements())
                .totalPages(paperPage.getTotalPages())
                .currentPage(paperPage.getNumber())
                .pageSize(paperPage.getSize())
                .hasNext(paperPage.hasNext())
                .hasPrev(paperPage.hasPrevious())
                .build();

        return ResponseEntity.ok(AppResponse.success("Papers retrieved", result));
    }

    @Operation(summary = "Export citation", description = "Get citation for a paper in BibTeX, RIS, APA, or MLA format. Single paper only.")
    @GetMapping("/{paperId}/citation")
    @Transactional(readOnly = true)
    public ResponseEntity<String> exportCitation(
            @PathVariable UUID paperId,
            @RequestParam(defaultValue = "bibtex") String format) {
        ResearchPaper paper = researchPaperRepository.findByIdWithAuthors(paperId)
                .orElseThrow(() -> new RuntimeException("Paper not found: " + paperId));

        String citation = citationService.generate(paper, format);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        headers.set("Content-Disposition",
                "attachment; filename=\"citation-" + paperId + "." + toFileExtension(format) + "\"");
        return ResponseEntity.ok().headers(headers).body(citation);
    }

    @Operation(summary = "Export multiple citations", description = "Get citations for multiple paper IDs in one request. Returns concatenated citations separated by blank lines.")
    @PostMapping("/citations/export")
    @Transactional(readOnly = true)
    public ResponseEntity<String> exportCitations(
            @RequestBody List<UUID> paperIds,
            @RequestParam(defaultValue = "bibtex") String format) {
        StringBuilder sb = new StringBuilder();

        for (UUID paperId : paperIds) {
            researchPaperRepository.findByIdWithAuthors(paperId).ifPresentOrElse(
                    paper -> {
                        sb.append(citationService.generate(paper, format));
                        sb.append("\n\n");
                    },
                    () -> sb.append("% Paper not found: ").append(paperId).append("\n\n")
            );
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        headers.set("Content-Disposition",
                "attachment; filename=\"citations-" + paperIds.size() + "papers." + toFileExtension(format) + "\"");
        return ResponseEntity.ok().headers(headers).body(sb.toString().trim());
    }

    private String toFileExtension(String format) {
        return switch (format.toLowerCase()) {
            case "bibtex" -> "bib";
            case "ris" -> "ris";
            case "apa", "mla" -> "txt";
            default -> "txt";
        };
    }

    @GetMapping("/search")
    public ResponseEntity<AppResponse<PaperSearchResultDTO>> searchPapers(
            @ModelAttribute @Valid PaperSearchRequestDTO request,
            Authentication authentication) {
        // Route to Neo4j graph search only for simple keyword queries with default "relevance" sort
        // When user requests a specific sort (citations/title/date), use SQL search with Pageable Sort
        if (isSimpleKeywordSearch(request) && "relevance".equalsIgnoreCase(
                request.getSortBy() != null ? request.getSortBy() : "relevance")) {
            String keyword = request.getQuery().trim();
            if (keyword.length() > KeywordConstants.MAX_KEYWORD_LENGTH) {
                keyword = keyword.substring(0, KeywordConstants.MAX_KEYWORD_LENGTH);
            }
            PaperSearchResultDTO result = paperSearchOrchestrator.searchByKeyword(keyword, authentication.getName());
            return ResponseEntity.ok(AppResponse.success("Search completed via graph", result));
        }
        // SQL search (supports user-controlled sorting via Pageable)
        return ResponseEntity.ok(AppResponse.success("Search completed", paperSearchService.searchPapers(request, authentication.getName())));
    }

    /** True if the request is a plain keyword search without author/journal filters. */
    private boolean isSimpleKeywordSearch(PaperSearchRequestDTO request) {
        String author = request.getAuthorName();
        String journal = request.getJournalId();
        return (author == null || author.isBlank()) && (journal == null || journal.isBlank());
    }

    @Operation(summary = "Search papers directly from OpenAlex", description = "Search papers by keyword via OpenAlex API with full pagination. Returns REAL total count from OpenAlex, not limited to local DB.")
    @GetMapping("/search/openalex")
    public ResponseEntity<AppResponse<PaperSearchResultDTO>> searchOpenAlex(
            @RequestParam("query") String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String keyword = query.trim();
        if (keyword.length() > KeywordConstants.MAX_KEYWORD_LENGTH) {
            keyword = keyword.substring(0, KeywordConstants.MAX_KEYWORD_LENGTH);
        }

        OpenAlexFallbackSearchService.PaginatedOpenAlexResult result =
                openAlexFallbackSearchService.searchOpenAlexPaginated(keyword, page, size);

        int safePage = Math.max(0, page);
        int safeSize = Math.min(50, Math.max(1, size));
        long totalElements = result.totalCount();
        int totalPages = totalElements > 0 ? (int) Math.ceil((double) totalElements / safeSize) : 0;

        PaperSearchResultDTO dto = PaperSearchResultDTO.builder()
                .papers(result.papers())
                .totalElements(totalElements)
                .totalPages(totalPages)
                .currentPage(safePage)
                .pageSize(safeSize)
                .hasNext(safePage + 1 < totalPages)
                .hasPrev(safePage > 0)
                .build();

        return ResponseEntity.ok(AppResponse.success("Papers retrieved from OpenAlex", dto));
    }

    @GetMapping("/search/author")
    public ResponseEntity<AppResponse<PaperSearchResultDTO>> searchByAuthor(
            @ModelAttribute @Valid PaperSearchRequestDTO request,
            Authentication authentication) {
        return ResponseEntity.ok(AppResponse.success("Author search completed", paperSearchService.searchByAuthor(request, authentication.getName())));
    }

    @GetMapping("/search/journal")
    public ResponseEntity<AppResponse<PaperSearchResultDTO>> searchByJournal(
            @ModelAttribute @Valid PaperSearchRequestDTO request,
            Authentication authentication) {
        return ResponseEntity.ok(AppResponse.success("Journal search completed", paperSearchService.searchByJournal(request, authentication.getName())));
    }

    @GetMapping("/filter/advanced")
    public ResponseEntity<AppResponse<PaperSearchResultDTO>> advancedFilter(
            @ModelAttribute @Valid PaperAdvancedFilterRequestDTO request,
            Authentication authentication) {
        return ResponseEntity.ok(AppResponse.success("Advanced filter completed", paperSearchService.advancedFilter(request, authentication.getName())));
    }

    @GetMapping("/{paperId}")
    public ResponseEntity<AppResponse<PaperDetailResponseDTO>> getPaperDetails(
            @PathVariable UUID paperId,
            @RequestParam(required = false) String sourceUrl,
            Authentication authentication) {
        return ResponseEntity.ok(AppResponse.success("Paper details retrieved",
                paperSearchService.getPaperDetails(paperId, sourceUrl, authentication.getName())));
    }

    @GetMapping("/usage")
    public ResponseEntity<AppResponse<UsageLimitResponseDTO>> getRemainingUsage(Authentication authentication) {
        return ResponseEntity.ok(AppResponse.success("Usage info retrieved", paperSearchService.getRemainingUsage(authentication.getName())));
    }

    @Operation(summary = "Check & consume search quota for a keyword", description = "Called when user presses Enter in search input. Only consumes quota if keyword is not cached and OpenAlex has data.")
    @GetMapping("/search/quota")
    public ResponseEntity<AppResponse<SearchQuotaResponseDTO>> checkSearchQuota(
            @RequestParam("query") String query,
            Authentication authentication) {
        SearchQuotaResponseDTO result = paperSearchOrchestrator.checkSearchQuota(query, authentication.getName());
        return ResponseEntity.ok(AppResponse.success("Quota check completed", result));
    }

    @Operation(summary = "Graph-based keyword search", description = "Search papers by keyword using Neo4j graph. Falls back to OpenAlex API if not found locally.")
    @GetMapping("/search/graph")
    public ResponseEntity<AppResponse<PaperSearchResultDTO>> graphSearch(
            @RequestParam("keyword") String keyword,
            Authentication authentication) {
        // Truncate keyword if too long (defense in depth)
        if (keyword.length() > KeywordConstants.MAX_KEYWORD_LENGTH) {
            keyword = keyword.substring(0, KeywordConstants.MAX_KEYWORD_LENGTH);
        }
        PaperSearchResultDTO result = paperSearchOrchestrator.searchByKeyword(keyword, authentication.getName());
        return ResponseEntity.ok(AppResponse.success("Papers retrieved via graph search", result));
    }

    // ── Quick summary DTO (bỏ qua authors cho list view để nhẹ) ──
    private PaperDetailResponseDTO toSummaryDTO(ResearchPaper paper) {
        List<KeywordDTO> keywords = paper.getKeywords() != null ? paper.getKeywords().stream()
                .filter(pk -> pk.getRelevanceScore() == null || pk.getRelevanceScore() != 1.0)
                .sorted(Comparator.comparing(
                        pk -> pk.getRelevanceScore() != null ? pk.getRelevanceScore() : 0.0d,
                        Comparator.reverseOrder()))
                .map(pk -> KeywordDTO.builder()
                        .keywordText(pk.getKeyword().getKeywordText())
                        .relevanceScore(pk.getRelevanceScore())
                        .build())
                .collect(Collectors.toList()) : List.of();

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
                .journalQuartile(paper.getJournal() != null ? paper.getJournal().getQuartile() : null)
                .journalImpactFactor(paper.getJournal() != null && paper.getJournal().getImpactFactor() != null
                        ? paper.getJournal().getImpactFactor().doubleValue() : null)
                .sourceUrl(paper.getDoi() != null ? "https://doi.org/" + paper.getDoi() : null)
                .pdfAvailable(Boolean.TRUE.equals(paper.getIsOpenAccess())
                        || (paper.getPdfUrl() != null && !paper.getPdfUrl().isBlank()))
                .pdfUrl(paper.getPdfUrl())
                .keywords(keywords)
                .rating(RatingCalculator.compute(paper.getJournal(), paper.getCitationCount(), null))
                .createdAt(paper.getCreatedAt())
                .build();
    }

    /**
     * Build Spring Sort from user-facing sort params for browse endpoints.
     */
    private Sort buildBrowseSort(String sortBy, String sortDirection) {
        if (sortBy == null || sortBy.isBlank()) sortBy = "date";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDirection)
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        String column = switch (sortBy.toLowerCase()) {
            case "citations" -> "citationCount";
            case "title" -> "title";
            case "date" -> "createdAt";
            default -> "createdAt";
        };
        return Sort.by(direction, column);
    }
}
