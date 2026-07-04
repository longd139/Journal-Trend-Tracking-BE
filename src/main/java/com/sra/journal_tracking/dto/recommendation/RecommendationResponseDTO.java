package com.sra.journal_tracking.dto.recommendation;

import com.sra.journal_tracking.dto.paper.PaperDetailResponseDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single recommended paper with its recommendation reason.
 * The {@code reason} enum indicates which strategy produced this recommendation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationResponseDTO {

    public enum Reason {
        CONTENT_BASED,
        COLLABORATIVE,
        TRENDING,
        SIMILAR_PAPER
    }

    private PaperDetailResponseDTO paper;
    private Reason reason;
    private String reasonDetail; // e.g. "Because you searched for 'machine learning'"
}
