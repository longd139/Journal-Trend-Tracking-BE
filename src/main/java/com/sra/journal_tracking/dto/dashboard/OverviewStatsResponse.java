package com.sra.journal_tracking.dto.dashboard;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OverviewStatsResponse {

    // ═══════════════════════════════════════
    // System-wide cards (when no ?authorId=)
    // ═══════════════════════════════════════

    /** Card 1: Total number of research papers tracked in the system. */
    private Long papersTracked;
    private Double papersTrackedGrowthRate;

    /** Card 2: Sum of all citation counts across all papers. */
    private Long totalCitations;
    private Double totalCitationsGrowthRate;

    /** Card 3: Number of new papers added in the current month. */
    private Long paperGrowth;
    private Double paperGrowthRate;

    /** Card 4: Total number of unique authors in the system. */
    private Long totalAuthors;
    private Double totalAuthorsGrowthRate;

    // ═══════════════════════════════════════
    // Author-specific cards (when ?authorId=)
    // ═══════════════════════════════════════

    /** Author's full name (resolved from authorId). */
    private String authorName;

    /** Card 1: Total papers published by this author. */
    private Long authorTotalPapers;

    /** Card 2: Total citations received by this author across all papers. */
    private Long authorTotalCitations;

    /** Card 3: h-index — the largest H such that H papers have at least H citations each. */
    private Integer authorHIndex;

    /** Card 4: Number of unique co-authors this author has collaborated with. */
    private Long authorCoAuthors;

    // Inner classes preserved for backward compat
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class GrowthTopicInfo {
        private String topicName;
        private java.math.BigDecimal growthRate;
        private Integer paperCount;
        private java.time.LocalDateTime calculatedAt;
    }
}
