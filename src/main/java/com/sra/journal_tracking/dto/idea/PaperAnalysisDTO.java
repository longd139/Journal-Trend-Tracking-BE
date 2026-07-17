package com.sra.journal_tracking.dto.idea;

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
public class PaperAnalysisDTO {
    private UUID paperId;
    private String title;
    private String pdfUrl;
    private String abstractText;
    private List<CriterionResult> criteria;
}
