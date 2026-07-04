package com.sra.journal_tracking.dto.recommendation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Paginated wrapper for recommendation results.
 * Mirrors {@link com.sra.journal_tracking.dto.paper.PaperSearchResultDTO} structure.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationResultDTO {
    private List<RecommendationResponseDTO> recommendations;
    private Long totalElements;
    private Integer totalPages;
    private Integer currentPage;
    private Integer pageSize;
    private Boolean hasNext;
    private Boolean hasPrev;
}
