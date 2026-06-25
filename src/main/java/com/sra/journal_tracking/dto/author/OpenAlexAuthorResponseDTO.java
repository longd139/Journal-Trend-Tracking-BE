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
