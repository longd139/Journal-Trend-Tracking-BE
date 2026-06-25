package com.sra.journal_tracking.dto.journal;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Journal prestige timeline — "Biểu đồ Xu hướng Uy tín".
 * <p>
 * Tracks journal impact and volume year-by-year, combining paper count
 * (bar chart) and average citations per paper (line chart) into a
 * dual-axis visualization. Shows whether the journal is rising,
 * stable, or declining in prestige.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JournalTimelineResponse {

    /** Journal ID (UUID). */
    private String journalId;

    /** Journal name. */
    private String journalName;

    /** ISSN. */
    private String issn;

    /** Publisher. */
    private String publisher;

    /** Current official Impact Factor. */
    private BigDecimal impactFactor;

    /** Current Quartile (Q1–Q4). */
    private String quartile;

    /** Total papers in database for this journal. */
    private Long totalPapers;

    /** Total citations across all papers. */
    private Long totalCitations;

    /** Year-by-year data points for the timeline chart. */
    private List<YearlyDataPoint> timeline;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class YearlyDataPoint {
        /** Publication year. */
        private Integer year;

        /** Number of papers published that year. */
        private Long paperCount;

        /** Sum of citations for papers published that year. */
        private Long citationCount;

        /** Average citations per paper (citationCount / paperCount). Quality indicator. */
        private Double avgCitationsPerPaper;
    }
}
