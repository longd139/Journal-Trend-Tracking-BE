package com.sra.journal_tracking.dto.analytics;

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
public class CountryTrendResponse {
    private String keyword;
    private Integer startYear;
    private Integer endYear;
    private List<CountryTimeline> countries;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CountryTimeline {
        private String country;
        private List<YearlyStats> timeline;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class YearlyStats {
        private Integer year;
        private Long paperCount;
        private Long citationCount;
    }
}
