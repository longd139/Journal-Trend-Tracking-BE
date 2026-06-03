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
}