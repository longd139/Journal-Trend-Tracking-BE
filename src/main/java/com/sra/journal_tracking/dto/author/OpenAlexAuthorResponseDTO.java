package com.sra.journal_tracking.dto.author;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * DTO for deserializing OpenAlex /authors API response.
 * Example: GET https://api.openalex.org/authors?search=Albert%20Einstein
 */
@Data
public class OpenAlexAuthorResponseDTO {

    private Meta meta;
    private List<AuthorResult> results;

    @Data
    public static class Meta {
        private Integer count;

        @JsonProperty("per_page")
        private Integer perPage;

        @JsonProperty("next_cursor")
        private String nextCursor;
    }

    @Data
    public static class AuthorResult {
        private String id;

        @JsonProperty("display_name")
        private String displayName;

        @JsonProperty("display_name_alternatives")
        private List<String> displayNameAlternatives;

        @JsonProperty("works_count")
        private Integer worksCount;

        @JsonProperty("cited_by_count")
        private Integer citedByCount;

        @JsonProperty("h_index")
        private Integer hIndex;

        @JsonProperty("i10_index")
        private Integer i10Index;

        @JsonProperty("summary_stats")
        private SummaryStats summaryStats;

        @JsonProperty("last_known_institution")
        private Institution lastKnownInstitution;

        private String orcid;

        @JsonProperty("counts_by_year")
        private List<YearlyCount> countsByYear;

        private List<TopicEntry> topics;
    }

    @Data
    public static class YearlyCount {
        private Integer year;

        @JsonProperty("works_count")
        private Integer worksCount;

        @JsonProperty("oa_works_count")
        private Integer oaWorksCount;

        @JsonProperty("cited_by_count")
        private Integer citedByCount;
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
    public static class Institution {
        private String id;

        @JsonProperty("display_name")
        private String displayName;

        /**
         * Institution type from OpenAlex: "education", "government", "nonprofit", etc.
         */
        private String type;
    }
}
