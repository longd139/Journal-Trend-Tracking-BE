package com.sra.journal_tracking.controller;

import com.sra.journal_tracking.constants.KeywordConstants;
import com.sra.journal_tracking.dto.GraphNode;
import com.sra.journal_tracking.dto.GraphResponse;
import com.sra.journal_tracking.dto.paper.PaperAdvancedFilterRequestDTO;
import com.sra.journal_tracking.dto.paper.PaperDetailResponseDTO;
import com.sra.journal_tracking.dto.paper.PaperSearchRequestDTO;
import com.sra.journal_tracking.dto.paper.PaperSearchResultDTO;
import com.sra.journal_tracking.dto.paper.UsageLimitResponseDTO;
import com.sra.journal_tracking.dto.response.AppResponse;
import com.sra.journal_tracking.entity.jpa.ResearchPaper;
import com.sra.journal_tracking.entity.jpa.SearchKeyword;
import com.sra.journal_tracking.repository.jpa.ResearchPaperRepository;
import com.sra.journal_tracking.repository.jpa.SearchKeywordRepository;
import com.sra.journal_tracking.service.GraphService;
import com.sra.journal_tracking.service.GroqService;
import com.sra.journal_tracking.service.PaperSearchOrchestrator;
import com.sra.journal_tracking.service.PaperSearchService;
import com.sra.journal_tracking.service.SearchKeywordService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/papers")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
public class PaperSearchController {

    private final PaperSearchService paperSearchService;
    private final PaperSearchOrchestrator paperSearchOrchestrator;
    private final ResearchPaperRepository researchPaperRepository;
    private final GroqService groqService;
    private final GraphService graphService;
    private final SearchKeywordService searchKeywordService;
    private final SearchKeywordRepository searchKeywordRepository;

    @Operation(summary = "Browse all papers", description = "Get all papers in database with pagination. No search required.")
    @GetMapping
    public ResponseEntity<AppResponse<PaperSearchResultDTO>> browsePapers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<ResearchPaper> paperPage = researchPaperRepository.findAll(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));

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

    @Operation(summary = "AI-enhanced keyword graph", description = "Expands keyword via Groq AI, then queries Neo4j for multi-level keyword graph with paper counts and search counts.")
    @GetMapping("/search/graph")
    public ResponseEntity<AppResponse<GraphResponse>> graphSearch(
            @RequestParam("keyword") String keyword,
            @RequestParam(defaultValue = "2") int depth) {

        if (keyword.length() > KeywordConstants.MAX_KEYWORD_LENGTH) {
            keyword = keyword.substring(0, KeywordConstants.MAX_KEYWORD_LENGTH);
        }

        String trimmedKeyword = keyword.trim();
        if (trimmedKeyword.isEmpty()) {
            return ResponseEntity.badRequest().body(
                    AppResponse.of(400, "Keyword is required", null));
        }

        // Clamp depth
        if (depth < 1) depth = 1;
        if (depth > 3) depth = 3;

        // ── Step 1: Record search for hot-keywords tracking ──
        try {
            searchKeywordService.recordSearch(trimmedKeyword);
        } catch (Exception e) {
            // non-blocking
        }

        // ── Step 2: Expand keyword via Groq AI (level 1) ──
        List<String> expandedKeywords = groqService.expandKeyword(trimmedKeyword);

        boolean aiAvailable = expandedKeywords.size() > 1
                || (expandedKeywords.size() == 1
                    && !expandedKeywords.get(0).equalsIgnoreCase(trimmedKeyword));

        List<String> allKeywords = new ArrayList<>();
        allKeywords.add(trimmedKeyword);
        if (aiAvailable) {
            allKeywords.addAll(expandedKeywords);
        }

        // ── Step 3: Build multi-level keyword graph ──
        GraphResponse graph = graphService.getKeywordOnlyGraph(trimmedKeyword, allKeywords, depth);

        // ── Step 4: Enrich nodes with search counts from SQL ──
        for (GraphNode node : graph.getNodes()) {
            String normalized = node.getLabel().toLowerCase().trim();
            Optional<SearchKeyword> sk = searchKeywordRepository.findByNormalizedText(normalized);
            sk.ifPresent(searchKeyword -> node.setSearchCount(searchKeyword.getSearchCount()));
        }

        String message = aiAvailable
                ? String.format("AI-enhanced keyword graph (depth=%d, %d suggestions, %d nodes)",
                    depth, expandedKeywords.size(), graph.getNodes().size())
                : String.format("Keyword graph (depth=%d, %d nodes, AI unavailable)",
                    depth, graph.getNodes().size());

        return ResponseEntity.ok(AppResponse.success(message, graph));
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
                .createdAt(paper.getCreatedAt())
                .build();
    }
}