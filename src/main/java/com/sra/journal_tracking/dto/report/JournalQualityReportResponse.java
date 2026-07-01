package com.sra.journal_tracking.dto.report;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Journal Quality Report — answers the question: "Is this journal prestigious and suitable for submission?"
 * Contains quartile ranking, impact factor, editorial preferences (recent keyword taste),
 * AI-style insight text, and overall publication stats.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JournalQualityReportResponse {

    /** Report type identifier for frontend routing. */
    @Builder.Default
    private String reportType = "JOURNAL_QUALITY_REPORT";

    /** Report title in Vietnamese: "Đánh giá chất lượng tạp chí: [Journal Name]" */
    private String reportTitle;

    /** Journal name. */
    private String journalName;

    /** ISSN (International Standard Serial Number). */
    private String issn;

    /** Publisher name. */
    private String publisher;

    /** Quartile ranking: Q1 (top 25%), Q2, Q3, or Q4. */
    private String quartile;

    /** Official Impact Factor from the journal metadata. */
    private BigDecimal impactFactor;

    /**
     * Composite score: official impact factor if available,
     * otherwise falls back to calculated CiteScore (totalCitations / totalPapers).
     */
    private Double score;

    /**
     * Editorial taste / preference — describes what topics the journal
     * has been prioritizing in the last 2 years.
     * Format: "Tạp chí đang ưu tiên đăng tải các nghiên cứu về {keywordA}, {keywordB} trong 2 năm gần đây."
     */
    private String taste;

    /** AI-style insight text in Vietnamese about the journal's quality and suitability. */
    private String insight;

    /** Total number of papers published in this journal (in our database). */
    private Long totalPapers;

    /** Total citations accumulated across all papers in this journal. */
    private Long totalCitations;

    /** Top keywords recently published in this journal (last 2 years). */
    private List<String> topKeywords;
}
