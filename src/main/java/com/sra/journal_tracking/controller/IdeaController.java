package com.sra.journal_tracking.controller;

import com.sra.journal_tracking.dto.idea.*;
import com.sra.journal_tracking.dto.response.AppResponse;
import com.sra.journal_tracking.service.IdeaAnalysisService;
import com.sra.journal_tracking.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Research Idea Analysis — AI-powered pipeline for evaluating
 * research ideas against existing literature.
 *
 * <h3>Endpoints</h3>
 * <ol>
 *   <li>POST /extract-keywords — AI extracts + suggests keywords from idea text</li>
 *   <li>POST /analyze — Full 4-step pipeline (search → evaluate → gap → lit review)</li>
 *   <li>GET /history — Paginated list of previous analyses</li>
 *   <li>GET /history/{id} — Load a single saved analysis</li>
 *   <li>DELETE /history/{id} — Delete an analysis</li>
 * </ol>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ideas")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
@Tag(name = "Research Idea Analysis", description = "AI-powered research idea evaluation and gap analysis")
public class IdeaController {

    private final IdeaAnalysisService ideaAnalysisService;

    // ═══════════════════════════════════════════════════════════════
    //  1. Extract Keywords
    // ═══════════════════════════════════════════════════════════════

    @Operation(
            summary = "Extract keywords from research idea",
            description = "AI extracts key research terms from the idea text and suggests " +
                          "additional related keywords to broaden the paper search."
    )
    @PostMapping("/extract-keywords")
    public ResponseEntity<AppResponse<ExtractKeywordsResponse>> extractKeywords(
            @Valid @RequestBody ExtractKeywordsRequest request,
            Authentication authentication) {

        ExtractKeywordsResponse response = ideaAnalysisService.extractKeywords(request.getIdeaText());
        return ResponseEntity.ok(AppResponse.success("Keywords extracted", response));
    }

    // ═══════════════════════════════════════════════════════════════
    //  2. Analyze
    // ═══════════════════════════════════════════════════════════════

    @Operation(
            summary = "Run full research idea analysis pipeline",
            description = "Searches for papers matching the selected keywords, downloads PDFs, " +
                          "evaluates each paper against 4 criteria (topic match, method relevant, " +
                          "gap addressed, cite worthy), performs cross-paper gap analysis, and " +
                          "generates a literature review draft. Results are persisted for history view."
    )
    @PostMapping("/analyze")
    public ResponseEntity<AppResponse<IdeaAnalysisResponse>> analyze(
            @Valid @RequestBody IdeaAnalysisRequest request,
            Authentication authentication) {

        UUID userId = extractUserId(authentication);
        IdeaAnalysisResponse response = ideaAnalysisService.analyze(
                request.getIdeaText(), request.getSelectedKeywords(), userId);
        return ResponseEntity.ok(AppResponse.success("Analysis complete", response));
    }

    // ═══════════════════════════════════════════════════════════════
    //  3. History List
    // ═══════════════════════════════════════════════════════════════

    @Operation(
            summary = "Get analysis history",
            description = "Returns a paginated list of the user's previous analyses, " +
                          "ordered by most recent first. Each item includes truncated idea text, " +
                          "keywords, paper count, and novelty score."
    )
    @GetMapping("/history")
    public ResponseEntity<AppResponse<HistoryListResponse>> getHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication) {

        UUID userId = extractUserId(authentication);
        HistoryListResponse response = ideaAnalysisService.getHistory(userId, page, size);
        return ResponseEntity.ok(AppResponse.success("History loaded", response));
    }

    // ═══════════════════════════════════════════════════════════════
    //  4. History Detail
    // ═══════════════════════════════════════════════════════════════

    @Operation(
            summary = "Get analysis detail",
            description = "Loads the full saved result of a previous analysis from the database. " +
                          "No AI calls are made — all data (papers, gap analysis, literature review) " +
                          "is loaded from the persisted result JSON."
    )
    @GetMapping("/history/{analysisId}")
    public ResponseEntity<AppResponse<IdeaAnalysisResponse>> getHistoryDetail(
            @PathVariable UUID analysisId,
            Authentication authentication) {

        UUID userId = extractUserId(authentication);
        IdeaAnalysisResponse response = ideaAnalysisService.getHistoryDetail(analysisId, userId);
        return ResponseEntity.ok(AppResponse.success("Analysis detail loaded", response));
    }

    // ═══════════════════════════════════════════════════════════════
    //  5. Delete History
    // ═══════════════════════════════════════════════════════════════

    @Operation(
            summary = "Delete an analysis",
            description = "Permanently deletes a saved analysis. The user must own the analysis."
    )
    @DeleteMapping("/history/{analysisId}")
    public ResponseEntity<AppResponse<Void>> deleteHistory(
            @PathVariable UUID analysisId,
            Authentication authentication) {

        UUID userId = extractUserId(authentication);
        ideaAnalysisService.deleteHistory(analysisId, userId);
        return ResponseEntity.noContent().build();
    }

    // ═══════════════════════════════════════════════════════════════
    //  Helper
    // ═══════════════════════════════════════════════════════════════

    private UUID extractUserId(Authentication authentication) {
        CustomUserDetails details = (CustomUserDetails) authentication.getPrincipal();
        return details.getUser().getUserId();
    }
}
