package com.sra.journal_tracking.dto.report;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Keyword Trend Report — answers the question: "Is this topic heating up or cooling down?"
 * Contains total papers, YoY growth rate, status classification,
 * AI-generated insight text, related keywords, and yearly breakdown for charts.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KeywordTrendReportResponse {

    /** Report type identifier for frontend routing. */
    @Builder.Default
    private String reportType = "KEYWORD_TREND_REPORT";

    /** Report title in Vietnamese: "Báo cáo phân tích chủ đề: [Keyword]" */
    private String reportTitle;

    /** The searched keyword. */
    private String keyword;

    /** Total number of research papers matching this keyword. */
    private Long totalPapers;

    /** Year-over-year growth rate as a percentage (e.g. +25.5 means 25.5% growth). */
    private Double yoyGrowthRate;

    /**
     * Trend status based on growth rate:
     * "Đang bùng nổ" (exploding, >20% growth),
     * "Ổn định" (stable, 0-20%),
     * "Bão hòa" (saturating, <0%).
     */
    private String status;

    /** AI-style insight text in Vietnamese describing the keyword trend. */
    private String insight;

    /** Top 3 related (co-occurring) keywords — research niches the user should consider. */
    private List<String> topRelatedKeywords;

    /** Yearly breakdown of paper counts for chart visualization. */
    private List<YearlyDataPoint> yearlyBreakdown;

    // ── Nested DTO ──

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class YearlyDataPoint {
        private Integer year;
        private Long paperCount;
    }
}
