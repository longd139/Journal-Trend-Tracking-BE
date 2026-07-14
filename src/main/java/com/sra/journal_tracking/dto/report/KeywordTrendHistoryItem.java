package com.sra.journal_tracking.dto.report;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Lightweight history item for cached keyword trend reports.
 * Contains keyword and timestamps only — full report data is retrieved
 * via the /cached endpoint when needed.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KeywordTrendHistoryItem {

    private String keyword;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
