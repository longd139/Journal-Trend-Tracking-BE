package com.sra.journal_tracking.dto.dashboard;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
public class OverviewStatsResponse {

    /**
     * Card 1: Total number of research papers tracked in the system.
     */
    private Long papersTracked;

    /**
     * Card 2: Sum of all citation counts across all papers.
     */
    private Long totalCitations;

    /**
     * Card 3: Number of research topics currently marked as trending.
     */
    private Long topTrendingNow;

    /**
     * Card 4: The topic with the highest GrowthRate from PUBLICATION_TREND.
     * Populated via GrowthTopicInfo sub-object.
     */
    private GrowthTopicInfo topGrowthTopic;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class GrowthTopicInfo {
        /**
         * Name of the research topic with the highest growth rate.
         * May be null if no publication trend data exists yet.
         */
        private String topicName;

        /**
         * The highest GrowthRate value (as percentage, e.g. 25.5 means 25.5%).
         */
        private BigDecimal growthRate;

        /**
         * Paper count in the latest period for this topic.
         */
        private Integer paperCount;

        /**
         * When this trend data was last calculated.
         */
        private LocalDateTime calculatedAt;
    }
}
