package com.sra.journal_tracking.controller;

import com.sra.journal_tracking.dto.response.AppResponse;
import com.sra.journal_tracking.dto.trend.PublicationTrendResponse;
import com.sra.journal_tracking.entity.jpa.PublicationTrend;
import com.sra.journal_tracking.repository.jpa.PublicationTrendRepository;
import com.sra.journal_tracking.repository.jpa.ResearchTopicRepository;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Public REST endpoints for publication trend data — time-series data showing
 * publication volume, citation counts, and growth rates for tracked topics,
 * fields, and journals. No authentication required (under /api/public/).
 */
@Slf4j
@RestController
@RequestMapping("/api/public/publication-trends")
@RequiredArgsConstructor
public class PublicationTrendController {

    private final PublicationTrendRepository publicationTrendRepository;
    private final ResearchTopicRepository researchTopicRepository;

    @Operation(
            summary = "Get publication trend timeline",
            description = "Returns paginated publication trend data points for a given period type "
                        + "(MONTHLY, WEEKLY, YEARLY). Each data point includes paper count, citation "
                        + "count, and growth rate for a specific period. Useful for rendering "
                        + "time-series line/bar charts on the Analytics page."
    )
    @GetMapping
    public ResponseEntity<AppResponse<List<PublicationTrendResponse>>> getTrends(
            @RequestParam(defaultValue = "MONTHLY") String periodType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {

        if (page < 0) page = 0;
        if (size < 1) size = 1;
        if (size > 100) size = 100;

        // Validate period type
        if (!List.of("MONTHLY", "WEEKLY", "YEARLY").contains(periodType.toUpperCase())) {
            throw new IllegalArgumentException("periodType must be MONTHLY, WEEKLY, or YEARLY");
        }

        log.info("Fetching publication trends: periodType={}, page={}, size={}", periodType, page, size);

        List<PublicationTrend> trends = publicationTrendRepository
                .findByPeriodTypeOrderByCalculatedAtDesc(periodType.toUpperCase(), PageRequest.of(page, size));

        List<PublicationTrendResponse> response = trends.stream()
                .map(this::mapToResponse)
                .toList();

        return ResponseEntity.ok(AppResponse.success("Publication trends retrieved", response));
    }

    @Operation(
            summary = "Get top growth topic",
            description = "Returns the single research topic with the highest growth rate. "
                        + "Uses the pre-computed PublicationTrend data with trendTarget='topic'. "
                        + "Useful for highlighting the hottest rising topic on dashboards."
    )
    @GetMapping("/top-growth")
    public ResponseEntity<AppResponse<PublicationTrendResponse>> getTopGrowthTopic() {

        log.info("Fetching top growth topic");

        PublicationTrend topGrowth = publicationTrendRepository.findTopGrowthTopic()
                .orElseThrow(() -> new IllegalStateException("No top growth topic found — trend data may not have been computed yet"));

        return ResponseEntity.ok(AppResponse.success("Top growth topic retrieved", mapToResponse(topGrowth)));
    }

    /**
     * Map PublicationTrend entity → PublicationTrendResponse DTO,
     * resolving the target name when possible.
     */
    private PublicationTrendResponse mapToResponse(PublicationTrend trend) {
        String targetName = resolveTargetName(trend.getTrendTarget(), trend.getTargetId());

        return PublicationTrendResponse.builder()
                .trendId(trend.getTrendId())
                .periodType(trend.getPeriodType())
                .periodValue(trend.getPeriodValue())
                .trendTarget(trend.getTrendTarget())
                .targetId(trend.getTargetId())
                .targetName(targetName)
                .paperCount(trend.getPaperCount())
                .citationCount(trend.getCitationCount())
                .growthRate(trend.getGrowthRate())
                .calculatedAt(trend.getCalculatedAt())
                .build();
    }

    /**
     * Resolve the display name for a trend target.
     * Currently resolves 'topic' targets by looking up the ResearchTopic name.
     * Falls back to the targetId string for unresolved targets.
     */
    private String resolveTargetName(String trendTarget, UUID targetId) {
        if (targetId == null) return null;

        try {
            if ("topic".equalsIgnoreCase(trendTarget)) {
                return researchTopicRepository.findById(targetId)
                        .map(topic -> topic.getTopicName())
                        .orElse(targetId.toString());
            }
            // Future: resolve 'field' and 'journal' targets here
        } catch (Exception e) {
            log.debug("Could not resolve target name for {}/{}: {}", trendTarget, targetId, e.getMessage());
        }

        return targetId.toString();
    }
}
