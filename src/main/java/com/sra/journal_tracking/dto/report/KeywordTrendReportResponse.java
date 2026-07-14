package com.sra.journal_tracking.dto.report;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Keyword Trend Report — comprehensive chart-ready data for a research keyword.
 * Contains publication & citation trends, co-occurring keywords with counts,
 * top journals, and an aggregated summary with peak-year and top-journal info.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KeywordTrendReportResponse {

    /** The searched keyword. */
    private String keyword;

    /** Report title in Vietnamese: "Báo cáo xu hướng: [Keyword]" */
    private String reportTitle;

    /** Aggregated summary statistics. */
    private Summary summary;

    /** Yearly publication counts for line/bar chart (last 5 years). */
    private List<TrendPoint> publicationTrend;

    /** Yearly citation counts for line/bar chart (last 5 years). */
    private List<TrendPoint> citationTrend;

    /** Top 8 co-occurring keywords with their paper counts. */
    private List<CoOccurringKeyword> coOccurringKeywords;

    /** Top journals publishing on this keyword with paper counts. */
    private List<TopJournal> topJournals;

    /** AI-style insight text in Vietnamese describing the keyword trend. */
    private String insight;

    // ── Inner DTOs ──

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Summary {
        /** Total number of research papers matching this keyword. */
        private Long totalPublications;

        /** Year with the highest number of publications. */
        private Integer peakYear;

        /** Total citation count across all matching papers. */
        private Long totalCitations;

        /** The journal with the most publications on this keyword. */
        private TopJournalInfo topJournal;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TopJournalInfo {
        private String name;
        private Integer paperCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TrendPoint {
        private Integer year;
        private Long count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CoOccurringKeyword {
        private String keyword;
        private Long count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TopJournal {
        private String name;
        private Long count;
    }
}
