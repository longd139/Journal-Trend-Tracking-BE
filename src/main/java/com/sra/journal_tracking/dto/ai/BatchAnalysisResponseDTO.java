package com.sra.journal_tracking.dto.ai;

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
public class BatchAnalysisResponseDTO {
    /** Per-paper summary items */
    private List<PaperSummaryItem> paperSummaries;

    /** Cross-paper comparative analysis (nullable when Gemini fails) */
    private String comparativeInsight;

    /** Count of papers successfully analyzed */
    private Integer papersAnalyzed;

    /**
     * Individual paper summary item within a batch analysis.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PaperSummaryItem {
        private UUID paperId;
        private String title;
        private String summary;       // 2-3 sentence summary (nullable)
        private String methodology;   // extracted methodology (nullable)
    }
}
