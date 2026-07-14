package com.sra.journal_tracking.dto.paper;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaperSearchRequestDTO {
    
    /**
     * Search query - can be paper title, abstract content, or keyword
     * Required field
     */
    private String query;
    
    /**
     * Optional: Filter by author name (exact or partial match)
     */
    private String authorName;
    
    /**
     * Optional: Filter by journal ID (UUID format)
     */
    private String journalId;
    
    /**
     * Pagination: page number (0-indexed)
     * Default: 0
     */
    @Min(value = 0, message = "Page index must not be less than 0")
    @Builder.Default
    private Integer page = 0;
    
    /**
     * Pagination: page size
     * Default: 10
     * Max: 50 (to prevent abuse)
     */
    @Min(value = 1, message = "Page size must not be less than 1")
    @Max(value = 50, message = "Page size must not be greater than 50")
    @Builder.Default
    private Integer size = 10;

    /**
     * Sort field. Supported values:
     * - "relevance" (default) — Neo4j graph relevance or full-text match score
     * - "citations" — sort by citationCount DESC
     * - "title" — sort by title alphabetically
     * - "date" — sort by pubDate DESC (newest first)
     */
    @Builder.Default
    private String sortBy = "relevance";

    /**
     * Optional: Filter by publication year from (inclusive).
     * When not provided, searches all years.
     */
    private Integer pubYearFrom;

    /**
     * Optional: Filter by publication year to (inclusive).
     * When not provided, searches all years.
     */
    private Integer pubYearTo;

    /**
     * Optional: Filter by open access status.
     * null = both, true = open access only, false = non-open-access only
     */
    private Boolean isOpenAccess;

    /**
     * Optional: Filter by journal quartile (comma-separated, e.g. "Q1,Q2").
     * When not provided, all quartiles are included.
     */
    private String quartile;

    /**
     * Sort direction. "asc" or "desc" (default: "desc").
     * Ignored when sortBy = "relevance".
     */
    @Builder.Default
    private String sortDirection = "desc";
}