package com.sra.journal_tracking.dto.paper;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Quick statistics for a searched keyword.
 * Returned alongside paper search results to give users immediate insight into
 * the keyword's academic impact — total papers, citations, YoY growth rate,
 * and average citations per paper (quality indicator).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KeywordQuickStatsResponse {

    /** The searched keyword (normalized). */
    private String keyword;

    /** Card 1: Number of papers in the system mentioning this keyword. */
    private Long totalPapers;

    /** Card 2: Sum of all citation counts across matched papers. */
    private Long totalCitations;

    /** Card 3: Year-over-year growth rate as percentage (e.g. +25.5 means 25.5% growth). */
    private Double yoyGrowthRate;

    /** Card 3 (auxiliary): Direction indicator for frontend arrow display. */
    private String yoyGrowthDirection;

    /** Card 4: Average citations per paper (totalCitations / totalPapers). Quality indicator. */
    private Double avgCitationsPerPaper;

    /** Auxiliary: Number of papers published this year matching the keyword. */
    private Long papersThisYear;

    /** Auxiliary: Number of papers published last year matching the keyword. */
    private Long papersLastYear;
}
