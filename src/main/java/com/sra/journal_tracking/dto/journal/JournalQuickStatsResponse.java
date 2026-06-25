package com.sra.journal_tracking.dto.journal;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Journal Key Performance Indicators (KPIs) — "Thẻ Định danh và Uy tín".
 * <p>
 * Returns journal prestige metrics: Impact Factor, Quartile (Q1–Q4),
 * total publications, and total citations. The quartile is color-coded
 * by the frontend (green=Q1, yellow=Q2, orange=Q3, red=Q4).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JournalQuickStatsResponse {

    /** Journal ID (UUID). */
    private String journalId;

    /** Journal name. */
    private String journalName;

    /** ISSN (International Standard Serial Number). */
    private String issn;

    /** Publisher name. */
    private String publisher;

    /** Card 1: Impact Factor (average citations per paper, 2-year window). */
    private BigDecimal impactFactor;

    /**
     * Card 1 (auxiliary): CiteScore — custom calculated as totalCitations/totalPapers
     * when official impactFactor is unavailable from external sources.
     */
    private Double calculatedCiteScore;

    /** Card 2: Quartile — Q1 (top 25%), Q2, Q3, or Q4. */
    private String quartile;

    /** Card 3: Total number of papers published (in our database). */
    private Long totalPapers;

    /** Card 4: Total citations accumulated across all papers. */
    private Long totalCitations;

    /** Auxiliary: Average citations per paper — quality indicator. */
    private Double avgCitationsPerPaper;

    /** Top keywords frequently published in this journal (top 5). */
    private List<String> topKeywords;
}
