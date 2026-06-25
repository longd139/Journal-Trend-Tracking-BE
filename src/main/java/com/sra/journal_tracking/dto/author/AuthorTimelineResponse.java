package com.sra.journal_tracking.dto.author;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * DTO for the Productivity & Impact Timeline chart.
 * Provides yearly paper counts (bar chart) and yearly citation counts (line chart)
 * so the FE can render a combined Bar + Line chart showing an author's
 * research trajectory over time.
 */
@Data
@Builder
public class AuthorTimelineResponse {

    /** Author's full name (from OpenAlex display_name) */
    private String fullName;

    /** OpenAlex author ID URL (e.g. "https://openalex.org/A5023888391") */
    private String openAlexId;

    /** Total number of papers published */
    private Integer totalPapers;

    /** Total number of citations received */
    private Integer totalCitations;

    /** h-index */
    private Integer hIndex;

    /** Year-by-year breakdown: papers published + citations received */
    private List<YearlyPoint> timeline;

    @Data
    @Builder
    public static class YearlyPoint {
        /** The year */
        private int year;

        /** Number of papers published in this year → Bar chart */
        private int worksCount;

        /** Number of citations received by papers published in this year → Line chart */
        private int citedByCount;
    }
}
