package com.sra.journal_tracking.dto.journal;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * DTO for deserializing OpenAlex /sources API response.
 * Example: GET https://api.openalex.org/sources?sort=works_count:desc&per-page=50
 */
@Data
public class OpenAlexSourceResponseDTO {

    private Meta meta;
    private List<SourceResult> results;

    @Data
    public static class Meta {
        private Integer count;

        @JsonProperty("per_page")
        private Integer perPage;

        @JsonProperty("next_cursor")
        private String nextCursor;
    }

    @Data
    public static class SourceResult {
        private String id;

        @JsonProperty("display_name")
        private String displayName;

        @JsonProperty("issn_l")
        private String issnL;

        /** Publisher name (host_organization_name in OpenAlex). */
        @JsonProperty("host_organization_name")
        private String hostOrganizationName;

        @JsonProperty("works_count")
        private Integer worksCount;

        @JsonProperty("cited_by_count")
        private Integer citedByCount;

        @JsonProperty("summary_stats")
        private SummaryStats summaryStats;

        private List<TopicEntry> topics;
    }

    @Data
    public static class SummaryStats {
        @JsonProperty("2yr_mean_citedness")
        private Double twoYearMeanCitedness;

        @JsonProperty("h_index")
        private Integer hIndex;

        @JsonProperty("i10_index")
        private Integer i10Index;
    }

    @Data
    public static class TopicEntry {
        private String id;

        @JsonProperty("display_name")
        private String displayName;

        private Integer count;

        @JsonProperty("subfield")
        private TopicLevel subfield;

        @JsonProperty("field")
        private TopicLevel field;

        @JsonProperty("domain")
        private TopicLevel domain;
    }

    @Data
    public static class TopicLevel {
        private String id;

        @JsonProperty("display_name")
        private String displayName;
    }
}
