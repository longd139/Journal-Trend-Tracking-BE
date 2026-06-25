package com.sra.journal_tracking.dto.paper;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single co-occurring keyword entry for the "Related Trends" / "Keyword Satellites"
 * advanced analytics section.
 * <p>
 * Shows keywords that frequently appear together with the searched keyword
 * in recent papers (last 2 years), helping users discover research niches.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RelatedKeywordResponse {

    /** The co-occurring keyword text (original casing). */
    private String keyword;

    /** Normalized (lowercase) form of the keyword — for deduplication. */
    private String normalizedKeyword;

    /** Total number of papers (last 2 years) that contain both keywords. */
    private Long cooccurrenceCount;

    /** Number of co-occurring papers published this year. */
    private Long thisYearCount;

    /** Number of co-occurring papers published last year. */
    private Long lastYearCount;

    /**
     * Year-over-year growth rate of the co-occurrence (percentage).
     * Null if not calculable (e.g., zero papers last year).
     */
    private Double growthRate;

    /**
     * Direction indicator for frontend arrow display: "up", "down", or "neutral".
     */
    private String growthDirection;
}
