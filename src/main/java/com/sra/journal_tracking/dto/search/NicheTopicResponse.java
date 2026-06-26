package com.sra.journal_tracking.dto.search;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NicheTopicResponse {

    /** The niche topic keyword text */
    private String keywordText;

    /** Normalized text for backend queries */
    private String normalizedText;

    /** Total papers containing both the category AND this niche topic */
    private Long paperCount;

    /** Papers from current year */
    private Long thisYearCount;

    /** Papers from last year */
    private Long lastYearCount;
}
