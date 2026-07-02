package com.sra.journal_tracking.dto.pdf;

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
public class PdfCandidateSearchResponse {
    private UUID requestId;
    private UUID paperId;
    private String paperTitle;
    private String doi;
    private List<PdfCandidateResponse> candidates;
}
