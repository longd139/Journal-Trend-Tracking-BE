package com.sra.journal_tracking.controller;

import com.sra.journal_tracking.constants.KeywordConstants;
import com.sra.journal_tracking.dto.paper.PaperAdvancedFilterRequestDTO;
import com.sra.journal_tracking.dto.paper.PaperDetailResponseDTO;
import com.sra.journal_tracking.dto.paper.PaperSearchRequestDTO;
import com.sra.journal_tracking.dto.paper.PaperSearchResultDTO;
import com.sra.journal_tracking.dto.paper.UsageLimitResponseDTO;
import com.sra.journal_tracking.dto.response.AppResponse;
import com.sra.journal_tracking.entity.jpa.ResearchPaper;
import com.sra.journal_tracking.repository.jpa.ResearchPaperRepository;
import com.sra.journal_tracking.service.PaperSearchOrchestrator;
import com.sra.journal_tracking.service.PaperSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import jakarta.validation.Valid;
import java.time.Year;
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
    private final ResearchPaperRepository researchPaperRepository;

    @Operation(summary = "Browse all papers", description = "Get all papers in database with pagination. No search required.")
    @GetMapping
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

    @GetMapping("/search")
    public ResponseEntity<AppResponse<PaperSearchResultDTO>> searchPapers(
            @ModelAttribute @Valid PaperSearchRequestDTO request,
            Authentication authentication) {
        return ResponseEntity.ok(AppResponse.success("Search completed", paperSearchService.searchPapers(request, authentication.getName())));
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
            Authentication authentication) {
        return ResponseEntity.ok(AppResponse.success("Paper details retrieved", paperSearchService.getPaperDetails(paperId, authentication.getName())));
    }

    @GetMapping("/usage")
    public ResponseEntity<AppResponse<UsageLimitResponseDTO>> getRemainingUsage(Authentication authentication) {
        return ResponseEntity.ok(AppResponse.success("Usage info retrieved", paperSearchService.getRemainingUsage(authentication.getName())));
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

    // ── Quick summary DTO (bỏ qua authors/keywords cho list view) ──
    private PaperDetailResponseDTO toSummaryDTO(ResearchPaper paper) {
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
                .sourceUrl(paper.getDoi() != null ? "https://doi.org/" + paper.getDoi() : null)
                .pdfAvailable(Boolean.TRUE.equals(paper.getIsOpenAccess())
                        || (paper.getPdfUrl() != null && !paper.getPdfUrl().isBlank()))
                .pdfUrl(paper.getPdfUrl())
                .createdAt(paper.getCreatedAt())
                .build();
    }
}
