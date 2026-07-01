package com.sra.journal_tracking.dto.follow;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Quick analytical insight for the dashboard "Báo cáo nhanh của bạn" card.
 * A personalized, auto-generated Vietnamese text summarizing the user's
 * followed topics and journals with actionable suggestions.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QuickInsightResponse {

    /** Auto-generated insight text in Vietnamese. */
    private String insight;

    /** When this insight was generated. */
    @Builder.Default
    private LocalDateTime generatedAt = LocalDateTime.now();
}
