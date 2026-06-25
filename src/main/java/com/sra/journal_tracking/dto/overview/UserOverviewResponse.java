package com.sra.journal_tracking.dto.overview;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserOverviewResponse {

    /**
     * Card 1: Number of papers the user has bookmarked/saved.
     * NOTE: Kept for backward compatibility but currently unused in the FE.
     */
    @Deprecated
    private Long savedPapers;

    /**
     * Card 1 (replacement): Total number of research papers in the system.
     */
    private Long totalPapers;

    /**
     * Card 2: Number of papers the user has viewed this month.
     */
    private Long papersViewed;

    /**
     * Card 3: Remaining search quota for the current month.
     * null for RESEARCHER / ADMIN (unlimited).
     */
    private Integer searchesRemaining;

    /**
     * Card 3 (companion): The monthly search limit.
     * null for RESEARCHER / ADMIN (unlimited).
     */
    private Integer monthlySearchLimit;

    /**
     * Card 4: Total number of unique keywords indexed in the system.
     */
    private Long totalKeywords;
}
