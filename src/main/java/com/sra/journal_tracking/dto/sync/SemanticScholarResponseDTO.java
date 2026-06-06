package com.sra.journal_tracking.dto.sync;

import java.util.List;
import lombok.Data;

@Data
public class SemanticScholarResponseDTO {
    private Integer total;
    private Integer offset;
    private List<SemanticScholarPaperDTO> data;

    @Data
    public static class SemanticScholarPaperDTO {
        private String paperId;
        private String title;
        @com.fasterxml.jackson.annotation.JsonProperty("abstract")
        private String abstractText;
        private Short year;
        private String publicationDate;
        private Boolean isOpenAccess;
        private Integer citationCount;
        private List<AuthorDTO> authors;
        private ExternalIds externalIds;

        @Data
        public static class AuthorDTO {
            private String authorId;
            private String name;
        }

        @Data
        public static class ExternalIds {
            private String DOI;
        }
    }
}
