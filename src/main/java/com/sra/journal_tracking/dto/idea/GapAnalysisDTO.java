package com.sra.journal_tracking.dto.idea;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GapAnalysisDTO {
    private List<GapArea> solvedAreas;
    private List<PartialArea> partiallyAddressed;
    private List<GapItem> researchGaps;
    private List<String> suggestedDirections;
    private Integer noveltyScore;
    private String noveltyExplanation;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GapArea {
        private String area;
        private List<String> papers;
        private String summary;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PartialArea {
        private String area;
        private List<String> papers;
        private String limitation;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GapItem {
        private String gap;
        private String rationale;
        private String suggestedDirection;
    }
}
