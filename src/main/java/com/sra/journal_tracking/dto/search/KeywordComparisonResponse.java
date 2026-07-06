package com.sra.journal_tracking.dto.search;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for keyword comparison endpoint.
 * Returns side-by-side stats for each requested keyword so the FE
 * can render a BarChart comparing paper counts, citations, etc.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KeywordComparisonResponse {

    /** List of keyword stat items, one per requested keyword. */
    private List<KeywordComparisonItem> keywords;

    /**
     * Individual keyword comparison data point.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class KeywordComparisonItem {

        /** The keyword name as requested. */
        private String name;

        /** Total number of papers matching this keyword. */
        private Long paperCount;

        /** Sum of all citation counts across matched papers. */
        private Long citationCount;

        /** Year-over-year growth rate as percentage (e.g. 12.5 = 12.5% growth). */
        private Double growthRate;

        /** The year with the most publications for this keyword. */
        private Integer topYear;
    }
}
