package com.sra.journal_tracking.dto.overview;

import java.util.List;

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
public class UserOverviewResponse {

    /**
     * Card 1: Number of papers the user has bookmarked/saved.
     * NOTE: Kept for backward compatibility but currently unused in the FE.
     */
    @Deprecated
    private Long savedPapers;

    /**
     * Card 1 (replacement): Total number of research papers in the system.
     */
    private Long totalPapers;

    /**
     * Card 2: Number of papers the user has viewed this month.
     */
    private Long papersViewed;

    /**
     * Card 3: Remaining search quota for the current month.
     * null for RESEARCHER / ADMIN (unlimited).
     */
    private Integer searchesRemaining;

    /**
     * Card 3 (companion): The monthly search limit.
     * null for RESEARCHER / ADMIN (unlimited).
     */
    private Integer monthlySearchLimit;

    /**
     * Card 4: Total number of unique keywords indexed in the system.
     */
    private Long totalKeywords;

    // ── Activity Summary ──

    /**
     * Number of bookmarks created by the user this month.
     */
    private Long bookmarksThisMonth;

    /**
     * Number of searches performed by the user this month.
     */
    private Integer searchesThisMonth;

    // ── NEW: Researcher-specific fields ──

    /**
     * Researcher's personal h-index, calculated from their authored papers.
     * null if the user has no matching author profile or no papers.
     */
    private Integer hIndex;

    /**
     * Year-by-year citation counts for the researcher's papers.
     * Empty list if no matching author or no papers.
     */
    private List<CitationYearEntry> citationHistory;

    /**
     * Top research fields/keywords breakdown as percentages.
     * Empty list if no matching author or no keyword data.
     */
    private List<ResearchFieldEntry> researchFields;

    /**
     * Most recent publications by the researcher (top 20).
     * Empty list if no matching author or no papers.
     */
    private List<RecentPublicationEntry> recentPublications;

    // ── Nested DTOs ──

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CitationYearEntry {
        /** Publication year (e.g., 2025). */
        private int y;
        /** Total citations received by papers published in that year. */
        private int citations;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ResearchFieldEntry {
        /** Keyword or research field name. */
        private String name;
        /** Percentage of total (values should sum to ~100). */
        private double value;
        /** Optional hex color — FE has a built-in palette fallback. */
        private String color;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RecentPublicationEntry {
        /** Paper unique identifier (UUID as string). */
        private String paperId;
        /** Full paper title. */
        private String title;
        /** Journal/publication venue name (null if not assigned to a journal). */
        private String journal;
        /** Publication year. */
        private int year;
        /** Author role: "First Author", "Co-Author", or "Corresponding Author". */
        private String role;
        /** Citation count. */
        private int citations;
    }
}
