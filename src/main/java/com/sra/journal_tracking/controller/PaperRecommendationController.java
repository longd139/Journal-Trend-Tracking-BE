package com.sra.journal_tracking.controller;

import com.sra.journal_tracking.dto.paper.PaperDetailResponseDTO;
import com.sra.journal_tracking.dto.recommendation.RecommendationResultDTO;
import com.sra.journal_tracking.dto.response.AppResponse;
import com.sra.journal_tracking.repository.jpa.ResearchPaperRepository;
import com.sra.journal_tracking.service.OpenAlexFallbackSearchService;
import com.sra.journal_tracking.service.PaperRecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for personalized paper recommendations and similar-paper discovery.
 * <p>
 * Both endpoints require JWT authentication (enforced by {@code .anyRequest().authenticated()}
 * in SecurityConfig, since they live under {@code /api/v1/papers/**}).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/papers")
@RequiredArgsConstructor
@Tag(name = "Paper Recommendations", description = "Personalized and content-based paper recommendation endpoints")
@SecurityRequirement(name = "Bearer Authentication")
public class PaperRecommendationController {

    private final PaperRecommendationService paperRecommendationService;
    private final ResearchPaperRepository researchPaperRepository;
    private final OpenAlexFallbackSearchService openAlexSearchService;

    @Operation(
            summary = "Get personalized paper recommendations",
            description = "Returns hybrid (content-based + collaborative) paper recommendations based on your search history, bookmarks, and follows. Falls back to trending papers for new users.")
    @GetMapping("/recommendations")
    public ResponseEntity<AppResponse<List<PaperDetailResponseDTO>>> getRecommendations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication) {

        int safePage = Math.max(0, page);
        int safeSize = Math.min(50, Math.max(1, size));
        short cy = (short) java.time.Year.now().getValue();

        // SQL: recent papers
        var papers = researchPaperRepository.findByPubYearBetween(
                (short) (cy - 2), cy,
                org.springframework.data.domain.PageRequest.of(safePage, safeSize));

        List<PaperDetailResponseDTO> result = papers.getContent().stream()
                .map(p -> PaperDetailResponseDTO.builder()
                        .paperId(p.getPaperId())
                        .title(p.getTitle())
                        .abstractText(p.getAbstractText())
                        .doi(p.getDoi())
                        .pubYear(p.getPubYear())
                        .citationCount(p.getCitationCount())
                        .isOpenAccess(p.getIsOpenAccess())
                        .sourceUrl(p.getDoi() != null ? "https://doi.org/" + p.getDoi() : null)
                        .build())
                .collect(java.util.stream.Collectors.toList());

        // Enrich citation counts from OpenAlex for papers with 0 in DB
        enrichCitationCounts(result);

        // OpenAlex fallback if SQL returns nothing
        if (result.isEmpty()) {
            try {
                result = openAlexSearchService.searchTopCited("machine learning", safeSize);
            } catch (Exception e) {
                log.warn("OpenAlex fallback failed: {}", e.getMessage());
            }
        }

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

    @Operation(
            summary = "Get trending papers (lightweight SQL-only)",
            description = "Returns top cited recent papers from SQL only — no Neo4j, no collaborative filtering."
    )
    @GetMapping("/trending")
    public ResponseEntity<AppResponse<List<PaperDetailResponseDTO>>> getTrending(
            @RequestParam(defaultValue = "10") int limit) {
        try {
            int safeLimit = Math.min(50, Math.max(1, limit));
            short cy = (short) java.time.Year.now().getValue();
            var page = researchPaperRepository.findByPubYearBetween(
                    (short) (cy - 2), cy,
                    org.springframework.data.domain.PageRequest.of(0, safeLimit));

            List<PaperDetailResponseDTO> result = page.getContent().stream()
                    .map(p -> PaperDetailResponseDTO.builder()
                            .paperId(p.getPaperId())
                            .title(p.getTitle())
                            .abstractText(p.getAbstractText())
                            .doi(p.getDoi())
                            .pubYear(p.getPubYear())
                            .citationCount(p.getCitationCount())
                            .isOpenAccess(p.getIsOpenAccess())
                            .sourceUrl(p.getDoi() != null ? "https://doi.org/" + p.getDoi() : null)
                            .build())
                    .toList();

            enrichCitationCounts(result);

            return ResponseEntity.ok(AppResponse.success("Trending papers retrieved", result));
        } catch (Exception e) {
            return ResponseEntity.ok(AppResponse.success(
                    "ERR: " + e.getClass().getSimpleName() + " - " + e.getMessage(), List.of()));
        }
    }

    /**
     * Enrich citation counts from OpenAlex for papers with 0 in the local DB.
     * Tries by DOI (which is always available) to fetch the real-time cited_by_count.
     */
    private void enrichCitationCounts(List<PaperDetailResponseDTO> papers) {
        for (PaperDetailResponseDTO p : papers) {
            if (p.getCitationCount() != null && p.getCitationCount() > 0) continue;
            if (p.getDoi() == null || p.getDoi().isBlank()) continue;
            try {
                Integer count = openAlexSearchService.fetchCitationCountByDoi(p.getDoi());
                if (count != null && count > 0) {
                    p.setCitationCount(count);
                }
            } catch (Exception ignored) {
                // best-effort enrichment
            }
        }
    }
}
