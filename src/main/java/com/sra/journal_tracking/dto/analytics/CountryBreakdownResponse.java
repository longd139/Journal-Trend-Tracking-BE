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
public class CountryBreakdownResponse {
    private String keyword;
    private Integer year;
    private List<CountryStats> countries;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CountryStats {
        private String country;
        private Long paperCount;
        private Long citationCount;
    }
}
