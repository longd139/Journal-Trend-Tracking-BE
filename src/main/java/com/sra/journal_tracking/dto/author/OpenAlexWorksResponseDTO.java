package com.sra.journal_tracking.dto.author;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * DTO for deserializing OpenAlex /works API response.
 * Only the fields needed for co-author aggregation are included.
 * Example: GET https://api.openalex.org/works?filter=authorships.author.id:A5112456378&per-page=200
 */
@Data
public class OpenAlexWorksResponseDTO {

    private WorkMeta meta;
    private List<WorkResult> results;

    @Data
    public static class WorkMeta {
        private Integer count;
        private Integer page;

        @JsonProperty("per_page")
        private Integer perPage;
    }

    @Data
    public static class WorkResult {
        private String id;
        private List<Authorship> authorships;
    }

    @Data
    public static class Authorship {
        private AuthorRef author;
        private List<InstitutionRef> institutions;

        @JsonProperty("author_position")
        private String authorPosition;
    }

    @Data
    public static class AuthorRef {
        private String id;

        @JsonProperty("display_name")
        private String displayName;
    }

    @Data
    public static class InstitutionRef {
        private String id;

        @JsonProperty("display_name")
        private String displayName;
    }
}
