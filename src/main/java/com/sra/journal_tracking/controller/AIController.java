package com.sra.journal_tracking.controller;

import com.sra.journal_tracking.dto.ai.BatchAnalysisResponseDTO;
import com.sra.journal_tracking.dto.ai.BatchAnalysisResponseDTO.PaperSummaryItem;
import com.sra.journal_tracking.dto.ai.MethodologyResponseDTO;
import com.sra.journal_tracking.dto.paper.AuthorDTO;
import com.sra.journal_tracking.dto.paper.KeywordDTO;
import com.sra.journal_tracking.dto.paper.PaperDetailResponseDTO;
import com.sra.journal_tracking.dto.response.AppResponse;
import com.sra.journal_tracking.entity.jpa.ResearchPaper;
import com.sra.journal_tracking.exception.AppException;
import com.sra.journal_tracking.exception.ErrorCode;
import com.sra.journal_tracking.repository.jpa.ResearchPaperRepository;
import com.sra.journal_tracking.service.AISummarizationService;
import com.sra.journal_tracking.service.BatchAnalysisResult;
import com.sra.journal_tracking.service.OpenAlexFallbackSearchService;
import com.sra.journal_tracking.service.PaperCacheService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * AI-powered endpoints for paper summarization, methodology extraction,
 * and batch paper analysis using the Gemini API.
 * <p>
 * All endpoints gracefully return null for AI fields when Gemini is unavailable —
 * no exceptions are thrown to the user for AI failures.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
@Tag(name = "AI Summarization", description = "AI-powered paper analysis using Gemini")
public class AIController {

    private final AISummarizationService aiSummarizationService;
    private final ResearchPaperRepository researchPaperRepository;
    private final OpenAlexFallbackSearchService openAlexFallbackSearchService;
    private final PaperCacheService paperCacheService;

    // ═══════════════════════════════════════════════════════════════
    //  ENDPOINTS
    // ═══════════════════════════════════════════════════════════════

    @Operation(
            summary = "Summarize a paper's abstract",
            description = "Generates a concise 2-3 sentence AI summary of the paper's abstract. " +
                          "Returns the paper detail DTO with aiSummary and methodology fields populated. " +
                          "When Gemini is unavailable, the AI fields are null and omitted from the response."
    )
    @GetMapping("/summarize/{paperId}")
    @Transactional(readOnly = true)
    public ResponseEntity<AppResponse<PaperDetailResponseDTO>> summarizeAbstract(
            @PathVariable UUID paperId,
            @RequestParam(required = false) String sourceUrl,
            Authentication authentication) {

        // Try DB first, fall back to OpenAlex
        PaperDetailResponseDTO dto;
        ResearchPaper paper = researchPaperRepository.findByIdWithAuthors(paperId).orElse(null);
        if (paper != null) {
            dto = mapToDetailDTO(paper);
            // If DB paper has no abstract, try fetching from cache or OpenAlex
            if (isBlank(dto.getAbstractText())) {
                log.info("Paper {} found in DB but has no abstract, trying fallback sources", paperId);
                String enrichedAbstract = fetchAbstractFromFallback(paperId, paper.getOpenAlexWorkId());
                if (enrichedAbstract != null) {
                    dto.setAbstractText(enrichedAbstract);
                }
            }
        } else if (sourceUrl != null && !sourceUrl.isBlank()) {
            dto = openAlexFallbackSearchService.getPaperByOpenAlexId(sourceUrl);
        } else {
            dto = openAlexFallbackSearchService.getPaperByUuid(paperId);
        }
        if (dto == null) throw new AppException(ErrorCode.RESOURCE_NOT_FOUND);

        // Populate AI fields — pass abstract from DTO so it works without DB
        String abstractText = dto.getAbstractText();
        String rawSummary = abstractText != null && !abstractText.isBlank()
                ? aiSummarizationService.summarizeAbstract(paperId, abstractText)
                : null;
        dto.setAiSummary(rawSummary);
        dto.setAiSummarySections(aiSummarizationService.parseSummarySections(rawSummary));
        dto.setMethodology(abstractText != null && !abstractText.isBlank()
                ? aiSummarizationService.extractMethodology(paperId, abstractText)
                : null);

        return ResponseEntity.ok(AppResponse.success("Paper details with AI summary", dto));
    }

    @Operation(
            summary = "Analyze a batch of papers",
            description = "Generates per-paper AI summaries and a cross-paper comparative insight " +
                          "for up to 10 papers at once. When Gemini is unavailable, summaries and " +
                          "insight are null."
    )
    @PostMapping("/batch-analyze")
    @Transactional(readOnly = true)
    public ResponseEntity<AppResponse<BatchAnalysisResponseDTO>> batchAnalyze(
            @RequestBody List<UUID> paperIds,
            Authentication authentication) {

        if (paperIds == null || paperIds.isEmpty()) {
            return ResponseEntity.ok(AppResponse.success("No papers provided",
                    BatchAnalysisResponseDTO.builder()
                            .paperSummaries(List.of())
                            .comparativeInsight(null)
                            .papersAnalyzed(0)
                            .build()));
        }

        BatchAnalysisResult result = aiSummarizationService.batchAnalyze(paperIds);

        // Build response DTO
        List<PaperSummaryItem> items = new ArrayList<>();
        Map<UUID, String> summaries = result.getPaperSummaries();

        for (Map.Entry<UUID, String> entry : summaries.entrySet()) {
            UUID id = entry.getKey();
            String summary = entry.getValue();

            // Fetch paper title (try to resolve)
            String title = null;
            try {
                title = researchPaperRepository.findById(id)
                        .map(ResearchPaper::getTitle)
                        .orElse(null);
            } catch (Exception e) {
                log.debug("Could not fetch title for paper {}: {}", id, e.getMessage());
            }

            items.add(PaperSummaryItem.builder()
                    .paperId(id)
                    .title(title)
                    .summary(summary)
                    .methodology(null) // batch doesn't extract per-paper methodology
                    .build());
        }

        int analyzed = (int) summaries.values().stream().filter(Objects::nonNull).count();

        BatchAnalysisResponseDTO response = BatchAnalysisResponseDTO.builder()
                .paperSummaries(items)
                .comparativeInsight(result.getComparativeInsight())
                .papersAnalyzed(analyzed)
                .build();

        return ResponseEntity.ok(AppResponse.success("Batch analysis completed", response));
    }

    @Operation(
            summary = "Extract research methodology from a paper",
            description = "Identifies the research methodology used in the paper based on its abstract. " +
                          "Returns a category like 'quantitative survey', 'RCT', 'case study', etc. " +
                          "When Gemini is unavailable, the methodology field is null."
    )
    @GetMapping("/methodology/{paperId}")
    @Transactional(readOnly = true)
    public ResponseEntity<AppResponse<MethodologyResponseDTO>> extractMethodology(
            @PathVariable UUID paperId,
            Authentication authentication) {

        ResearchPaper paper = researchPaperRepository.findByIdWithAuthors(paperId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));

        String methodology = aiSummarizationService.extractMethodology(paperId);

        MethodologyResponseDTO response = MethodologyResponseDTO.builder()
                .paperId(paper.getPaperId())
                .title(paper.getTitle())
                .methodology(methodology)
                .build();

        return ResponseEntity.ok(AppResponse.success("Methodology extracted", response));
    }

    // ═══════════════════════════════════════════════════════════════
    //  MAPPING (mirrors PaperSearchOrchestrator.mapToDetailDTO)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Try to fetch the abstract for a paper from fallback sources
     * when the local DB record has no abstract.
     * 1. PaperCache (7-day TTL, populated during OpenAlex searches)
     * 2. OpenAlex API (if the paper has an openAlexWorkId)
     */
    private String fetchAbstractFromFallback(UUID paperId, String openAlexWorkId) {
        // 1. Try PaperCache first (fast, local)
        try {
            var cached = paperCacheService.get(paperId);
            if (cached.isPresent() && !isBlank(cached.get().getAbstractText())) {
                log.info("Abstract found in PaperCache for paper {}", paperId);
                return cached.get().getAbstractText();
            }
        } catch (Exception e) {
            log.debug("PaperCache lookup failed for {}: {}", paperId, e.getMessage());
        }

        // 2. Try OpenAlex API if we have a work ID
        if (openAlexWorkId != null && !openAlexWorkId.isBlank()) {
            try {
                PaperDetailResponseDTO fromOpenAlex =
                        openAlexFallbackSearchService.getPaperByOpenAlexId(openAlexWorkId);
                if (fromOpenAlex != null && !isBlank(fromOpenAlex.getAbstractText())) {
                    log.info("Abstract fetched from OpenAlex for paper {}", paperId);
                    return fromOpenAlex.getAbstractText();
                }
            } catch (Exception e) {
                log.debug("OpenAlex fallback failed for {}: {}", paperId, e.getMessage());
            }
        }

        return null;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
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
                .filter(pk -> !(pk.getRelevanceScore() != null
                        && Double.compare(pk.getRelevanceScore(), 1.0d) == 0))
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
                .viewCount(0L)
                .bookmarkCount(0L)
                .createdAt(paper.getCreatedAt())
                .build();
    }
}
