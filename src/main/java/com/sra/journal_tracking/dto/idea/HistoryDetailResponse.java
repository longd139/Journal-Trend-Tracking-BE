package com.sra.journal_tracking.dto.idea;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Identical shape to {@link IdeaAnalysisResponse}, used when loading
 * a single historical analysis from the database.
 * The frontend can reuse the same component for both live and historical results.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistoryDetailResponse {
    private String analysisId;
    private String ideaText;
    private java.util.List<String> keywords;
    private java.util.List<PaperAnalysisDTO> papers;
    private GapAnalysisDTO gapAnalysis;
    private LiteratureReviewDTO literatureReview;
    private String createdAt;
}
