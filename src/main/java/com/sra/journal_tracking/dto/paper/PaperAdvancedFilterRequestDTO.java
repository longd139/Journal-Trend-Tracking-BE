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
public class PaperAdvancedFilterRequestDTO {
    
    /**
     * Optional: Filter papers published from this year (inclusive)
     * Format: 2020, 2021, etc.
     */
    private Short pubYearFrom;
    
    /**
     * Optional: Filter papers published up to this year (inclusive)
     * Format: 2024, 2025, etc.
     * Must be >= pubYearFrom if both provided
     */
    private Short pubYearTo;
    
    /**
     * Optional: Filter by research field ID (UUID format)
     */
    private String fieldId;

    /**
     * Optional: Filter by journal ID (UUID format)
     */
    private String journalId;
    
    /**
     * Optional: Filter only open access papers
     * If null: include both open access and closed
     * If true: only open access papers
     * If false: only closed access papers
     */
    private Boolean isOpenAccess;
    
    /**
     * Optional: Minimum citation count
     * Example: 10 -> only papers with citationCount >= 10
     */
    @Min(value = 0, message = "Minimum citations must not be less than 0")
    private Integer minCitations;
    
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
     * Max: 50
     */
    @Min(value = 1, message = "Page size must not be less than 1")
    @Max(value = 50, message = "Page size must not be greater than 50")
    @Builder.Default
    private Integer size = 10;
}
