package com.sra.journal_tracking.controller;

import com.sra.journal_tracking.dto.recommendation.RecommendationResultDTO;
import com.sra.journal_tracking.dto.response.AppResponse;
import com.sra.journal_tracking.service.PaperRecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for personalized paper recommendations and similar-paper discovery.
 * <p>
 * Both endpoints require JWT authentication (enforced by {@code .anyRequest().authenticated()}
 * in SecurityConfig, since they live under {@code /api/v1/papers/**}).
 */
@RestController
@RequestMapping("/api/v1/papers")
@RequiredArgsConstructor
@Tag(name = "Paper Recommendations", description = "Personalized and content-based paper recommendation endpoints")
@SecurityRequirement(name = "Bearer Authentication")
public class PaperRecommendationController {

    private final PaperRecommendationService paperRecommendationService;

    @Operation(
            summary = "Get personalized paper recommendations",
            description = "Returns hybrid (content-based + collaborative) paper recommendations based on your search history, bookmarks, and follows. Falls back to trending papers for new users.")
    @GetMapping("/recommendations")
    public ResponseEntity<AppResponse<RecommendationResultDTO>> getRecommendations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication) {

        int safePage = Math.max(0, page);
        int safeSize = Math.min(50, Math.max(1, size));

        RecommendationResultDTO result = paperRecommendationService
                .getPersonalizedRecommendations(authentication.getName(), safePage, safeSize);

        return ResponseEntity.ok(AppResponse.success("Recommendations retrieved", result));
    }

    @Operation(
            summary = "Get similar papers",
            description = "Finds papers similar to the specified paper based on shared keywords and research field overlap.")
    @GetMapping("/{paperId}/similar")
    public ResponseEntity<AppResponse<RecommendationResultDTO>> getSimilarPapers(
            @PathVariable UUID paperId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication) {

        int safePage = Math.max(0, page);
        int safeSize = Math.min(50, Math.max(1, size));

        RecommendationResultDTO result = paperRecommendationService
                .getSimilarPapers(authentication.getName(), paperId, safePage, safeSize);

        return ResponseEntity.ok(AppResponse.success("Similar papers retrieved", result));
    }
}
