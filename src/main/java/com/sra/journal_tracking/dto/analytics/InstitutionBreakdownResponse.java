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
public class InstitutionBreakdownResponse {
    private String keyword;
    private Integer year;
    private Integer limit;
    private List<InstitutionStats> institutions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InstitutionStats {
        private String institution;
        private Long paperCount;
        private Long citationCount;
    }
}
