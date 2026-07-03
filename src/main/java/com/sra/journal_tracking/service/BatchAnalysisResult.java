package com.sra.journal_tracking.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

/**
 * Internal holder for batch analysis results — not exposed as a DTO directly.
 * Mapped to {@link com.sra.journal_tracking.dto.ai.BatchAnalysisResponseDTO} by the controller.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchAnalysisResult {
    /** paperId → 2-3 sentence AI-generated summary */
    private Map<UUID, String> paperSummaries;

    /** Cross-paper comparative insight (nullable if Gemini unavailable or parse fails) */
    private String comparativeInsight;
}
