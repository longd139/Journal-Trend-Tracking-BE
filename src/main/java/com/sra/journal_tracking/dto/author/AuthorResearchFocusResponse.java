package com.sra.journal_tracking.dto.author;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * DTO for the Research Focus Pie Chart / Treemap.
 * Shows an author's topic distribution — which research areas they
 * publish in most, with counts suitable for proportional visualization.
 */
@Data
@Builder
public class AuthorResearchFocusResponse {

    /** Author's full name (from OpenAlex display_name) */
    private String fullName;

    /** OpenAlex author ID URL */
    private String openAlexId;

    /** Total number of papers published */
    private Integer totalPapers;

    /** Total number of topics the author has published in */
    private Integer totalTopics;

    /** Top topics with paper counts, sorted by count descending */
    private List<TopicFocus> topics;

    @Data
    @Builder
    public static class TopicFocus {
        /** Topic display name (e.g. "Topic Modeling", "Computer Vision") */
        private String topicName;

        /** Number of papers in this topic → determines pie slice / treemap area */
        private int paperCount;

        /** Percentage of total papers (0-100) — convenience for FE labels */
        private double percentage;

        /** Sub-discipline (e.g. "Artificial Intelligence") */
        private String subfield;

        /** Broader field (e.g. "Computer Science") */
        private String field;

        /** Top-level domain (e.g. "Physical Sciences") */
        private String domain;
    }
}
