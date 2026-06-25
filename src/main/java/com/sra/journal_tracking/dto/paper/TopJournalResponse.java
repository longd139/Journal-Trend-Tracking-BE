package com.sra.journal_tracking.dto.paper;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * A single entry in the "Top Journals" horizontal bar chart for a keyword.
 * Shows which journals publish the most papers on this topic.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TopJournalResponse {

    /** Journal name (bar label). */
    private String journalName;

    /** Number of papers published by this journal matching the keyword (bar value). */
    private Long paperCount;

    /** Journal impact factor (optional — helps evaluate journal prestige). */
    private BigDecimal impactFactor;

    /** Journal quartile: Q1, Q2, Q3, Q4 (optional). */
    private String quartile;

    /** Publisher name (optional). */
    private String publisher;
}
