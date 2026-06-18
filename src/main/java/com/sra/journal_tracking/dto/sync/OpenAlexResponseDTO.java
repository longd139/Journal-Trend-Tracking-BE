package com.sra.journal_tracking.dto.sync;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class OpenAlexResponseDTO {
    private Meta meta;
    private List<OpenAlexWorkDTO> results;

    @Data
    public static class Meta {
        private Integer count;

        @JsonProperty("per_page")
        private Integer perPage;
    }

    @Data
    public static class OpenAlexWorkDTO {
        private String id;
        private String doi;
        private String title;

        @JsonProperty("display_name")
        private String displayName;

        @JsonProperty("publication_year")
        private Short publicationYear;

        @JsonProperty("publication_date")
        private String publicationDate;

        @JsonProperty("cited_by_count")
        private Integer citedByCount;

        @JsonProperty("abstract_inverted_index")
        private Map<String, List<Integer>> abstractInvertedIndex;

        @JsonProperty("open_access")
        private OpenAccess openAccess;

        private List<Authorship> authorships;

        private List<Concept> concepts;
    }

    @Data
    public static class Concept {
        private String id;

        @JsonProperty("display_name")
        private String displayName;

        private Double score;
        private Integer level;
    }

    @Data
    public static class OpenAccess {
        @JsonProperty("is_oa")
        private Boolean isOa;
    }

    @Data
    public static class Authorship {
        private AuthorInfo author;

        @JsonProperty("raw_author_name")
        private String rawAuthorName;

        @JsonProperty("raw_affiliation_strings")
        private List<String> rawAffiliationStrings;
    }

    @Data
    public static class AuthorInfo {
        private String id;

        @JsonProperty("display_name")
        private String displayName;
    }
}
