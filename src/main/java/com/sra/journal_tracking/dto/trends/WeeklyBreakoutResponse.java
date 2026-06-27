package com.sra.journal_tracking.dto.trends;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * A single card in the "Xu hướng bứt phá tuần này" (Weekly Breakout Topics) section.
 * Contains keyword info, 6-month citation sparkline data, and growth classification.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WeeklyBreakoutResponse {

    /** The keyword/topic name (original casing from Neo4j) */
    private String keywordText;

    /**
     * 6 monthly citation count values for the sparkline chart.
     * Index 0 = oldest month (6 months ago), index 5 = current month.
     */
    private List<Long> sparkline;

    /** Classification label: "Strong Growth" | "Newly Emerging" | "Leading" */
    private String growthLabel;

    /** Total number of papers for this keyword (from Neo4j aggregation) */
    private Long totalPapers;

    /** Growth rate as a percentage (rounded to 1 decimal). null if not calculable. */
    private Double growthRate;
}
