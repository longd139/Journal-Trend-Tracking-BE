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

        @JsonProperty("next_cursor")
        private String nextCursor;
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

        @JsonProperty("primary_location")
        private PrimaryLocation primaryLocation;

        private List<Topic> topics;
        private List<Keyword> keywords;
        private List<Authorship> authorships;

        @JsonProperty("best_oa_location")
        private BestOaLocation bestOaLocation;
    }

    @Data
    public static class OpenAccess {
        @JsonProperty("is_oa")
        private Boolean isOa;

        @JsonProperty("oa_url")
        private String oaUrl;

        @JsonProperty("any_repository_has_fulltext")
        private Boolean anyRepositoryHasFulltext;
    }

    @Data
    public static class PrimaryLocation {
        private Source source;

        @JsonProperty("pdf_url")
        private String pdfUrl;

        @JsonProperty("landing_page_url")
        private String landingPageUrl;
    }

    @Data
    public static class BestOaLocation {
        @JsonProperty("is_oa")
        private Boolean isOa;

        @JsonProperty("pdf_url")
        private String pdfUrl;

        @JsonProperty("landing_page_url")
        private String landingPageUrl;
    }

    @Data
    public static class Source {
        private String id;

        @JsonProperty("display_name")
        private String displayName;

        @JsonProperty("issn_l")
        private String issnL;

        private String publisher;

        @JsonProperty("host_organization_name")
        private String hostOrganizationName;
    }

    @Data
    public static class Topic {
        private String id;

        @JsonProperty("display_name")
        private String displayName;

        private Double score;
        private TopicField field;
        private TopicField domain;
    }

    @Data
    public static class TopicField {
        private String id;

        @JsonProperty("display_name")
        private String displayName;
    }

    @Data
    public static class Keyword {
        private String id;

        @JsonProperty("display_name")
        private String displayName;

        private Double score;
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
