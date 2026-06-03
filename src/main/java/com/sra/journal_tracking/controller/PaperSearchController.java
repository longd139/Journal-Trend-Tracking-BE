package com.sra.journal_tracking.controller;

import com.sra.journal_tracking.dto.paper.PaperAdvancedFilterRequestDTO;
import com.sra.journal_tracking.dto.paper.PaperDetailResponseDTO;
import com.sra.journal_tracking.dto.paper.PaperSearchRequestDTO;
import com.sra.journal_tracking.dto.paper.PaperSearchResultDTO;
import com.sra.journal_tracking.dto.paper.UsageLimitResponseDTO;
import com.sra.journal_tracking.service.PaperSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import jakarta.validation.Valid;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/papers")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
public class PaperSearchController {

    private final PaperSearchService paperSearchService;

    @GetMapping("/search")
    public ResponseEntity<PaperSearchResultDTO> searchPapers(
            @ModelAttribute @Valid PaperSearchRequestDTO request,
            Authentication authentication) {
        return ResponseEntity.ok(paperSearchService.searchPapers(request, authentication.getName()));
    }

    @GetMapping("/search/author")
    public ResponseEntity<PaperSearchResultDTO> searchByAuthor(
            @ModelAttribute @Valid PaperSearchRequestDTO request,
            Authentication authentication) {
        return ResponseEntity.ok(paperSearchService.searchByAuthor(request, authentication.getName()));
    }

    @GetMapping("/search/journal")
    public ResponseEntity<PaperSearchResultDTO> searchByJournal(
            @ModelAttribute @Valid PaperSearchRequestDTO request,
            Authentication authentication) {
        return ResponseEntity.ok(paperSearchService.searchByJournal(request, authentication.getName()));
    }

    @GetMapping("/filter/advanced")
    public ResponseEntity<PaperSearchResultDTO> advancedFilter(
            @ModelAttribute @Valid PaperAdvancedFilterRequestDTO request,
            Authentication authentication) {
        return ResponseEntity.ok(paperSearchService.advancedFilter(request, authentication.getName()));
    }

    @GetMapping("/{paperId}")
    public ResponseEntity<PaperDetailResponseDTO> getPaperDetails(
            @PathVariable UUID paperId,
            Authentication authentication) {
        return ResponseEntity.ok(paperSearchService.getPaperDetails(paperId, authentication.getName()));
    }

    @GetMapping("/usage")
    public ResponseEntity<UsageLimitResponseDTO> getRemainingUsage(Authentication authentication) {
        return ResponseEntity.ok(paperSearchService.getRemainingUsage(authentication.getName()));
    }
}