package com.sra.journal_tracking.dto.journal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lightweight DTO for journal autocomplete suggestions from OpenAlex sources API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JournalSuggestionResponse {
    private String id;           // OpenAlex source ID (e.g. "https://openalex.org/S...")
    private String name;         // Journal name
    private String issn;         // ISSN-L
    private String publisher;    // Publisher name
    private Integer totalWorks;  // Total papers in this journal
    private Integer totalCitations; // Total citations
    private String quartile;     // Q1/Q2/Q3/Q4 if available
}
