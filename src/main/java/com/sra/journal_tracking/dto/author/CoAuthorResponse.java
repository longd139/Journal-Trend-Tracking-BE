package com.sra.journal_tracking.dto.author;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * DTO for the Co-author Collaboration Network.
 * Shows an author's most frequent co-authors — useful for exploring
 * research labs and collaboration ecosystems.
 */
@Data
@Builder
public class CoAuthorResponse {

    /** Author's full name */
    private String fullName;

    /** OpenAlex author ID URL */
    private String openAlexId;

    /** Total number of papers analyzed */
    private int totalPapersAnalyzed;

    /** Total number of unique co-authors found */
    private int totalCoAuthors;

    /**
     * Data source: "database" if results came from local DB,
     * "openalex" if fetched live from OpenAlex API.
     */
    private String dataSource;

    /** Top co-authors sorted by collaboration count descending */
    private List<CoAuthorEntry> coAuthors;

    @Data
    @Builder
    public static class CoAuthorEntry {
        /** Co-author's display name */
        private String name;

        /** OpenAlex author ID URL */
        private String openAlexId;

        /** Number of papers co-authored together */
        private int collaborationCount;

        /** Last known institution of the co-author */
        private String lastInstitution;
    }
}
