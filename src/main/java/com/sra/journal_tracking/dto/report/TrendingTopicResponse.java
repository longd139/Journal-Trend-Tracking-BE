package com.sra.journal_tracking.dto.report;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Trending Topic — a single entry in the dashboard "Trending Topics" leaderboard.
 * Contains keyword metadata, YoY growth rate, status classification,
 * AI-style insight text, and sparkline data for a mini trend chart.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TrendingTopicResponse {

    /** Report type identifier for frontend routing. */
    @Builder.Default
    private String reportType = "TRENDING_TOPICS";

    /** The keyword / research topic name (original casing from Neo4j). */
    private String keyword;

    /** Total number of research papers matching this keyword. */
    private Long totalPapers;

    /** Year-over-year growth rate as a percentage (e.g. +25.5 means 25.5% growth). */
    private Double yoyGrowthRate;

    /**
     * Trend status based on growth rate:
     * "Đang bùng nổ" (exploding, &gt;20% growth),
     * "Ổn định" (stable, 0-20%),
     * "Bão hòa" (saturating, &lt;0%).
     */
    private String status;

    /** AI-style insight text in Vietnamese describing the keyword trend. */
    private String insight;

    /** Yearly data points for sparkline chart visualization (last 5 years). */
    private List<SparklinePoint> sparkline;

    // ── Nested DTO ──

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SparklinePoint {
        private Integer year;
        private Long paperCount;
    }
}
