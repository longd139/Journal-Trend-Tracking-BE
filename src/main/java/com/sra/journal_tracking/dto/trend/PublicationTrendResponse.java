package com.sra.journal_tracking.dto.trend;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for a PublicationTrend entity.
 * Represents a single data point in a publication trend timeline —
 * paper count, citation count, and growth rate for a given period.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PublicationTrendResponse {

    /** Unique trend record identifier. */
    private UUID trendId;

    /** Period granularity: MONTHLY, WEEKLY, YEARLY. */
    private String periodType;

    /** The period this data point covers (e.g. "2026-07", "2026-W27", "2026"). */
    private String periodValue;

    /** What this trend tracks: 'topic', 'field', or 'journal'. */
    private String trendTarget;

    /** ID of the tracked entity (topic ID, field ID, or journal ID). */
    private UUID targetId;

    /** Resolved name of the tracked entity (topic name, field name, or journal name). */
    private String targetName;

    /** Number of papers published in this period. */
    private Integer paperCount;

    /** Total citations received by papers in this period. */
    private Integer citationCount;

    /** Growth rate compared to the previous period (percentage). */
    private BigDecimal growthRate;

    /** When this trend data point was calculated. */
    private LocalDateTime calculatedAt;
}
