package com.sra.journal_tracking.dto.overview;

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
public class OverviewStatisticsResponse {

    private StatCard papersTracked;
    private StatCard totalCitations;
    private StatCard topTrendingNow;
    private StatCard topGrowthTopic;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class StatCard {
        private String label;
        private Long value;
        private Double growthPercent;
        private String growthLabel;
        private String growthDirection;
        private String topicName;
    }
}
