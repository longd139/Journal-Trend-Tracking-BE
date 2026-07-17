package com.sra.journal_tracking.dto.idea;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IdeaAnalysisResponse {
    private UUID analysisId;
    private String ideaText;
    private List<String> keywords;
    private List<PaperAnalysisDTO> papers;
    private GapAnalysisDTO gapAnalysis;
    private LiteratureReviewDTO literatureReview;
    private String createdAt;
}
