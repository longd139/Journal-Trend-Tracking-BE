package com.sra.journal_tracking.dto.search;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TrendingKeywordResponse {

    /** The trending keyword text */
    private String keywordText;

    /** Number of recent papers mentioning this topic (from OpenAlex aggregation) */
    private Integer paperCount;

    /** Data source (e.g., "openalex") */
    private String source;

    /** Display order (1-based) for consistent ordering on the frontend */
    private Integer displayOrder;
}
