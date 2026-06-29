package com.sra.journal_tracking.dto.report;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Author Impact Report — answers the question: "Is this author a top expert in their field?"
 * Contains total papers, h-index, activity status,
 * AI-style insight text, top research field, and collaboration network.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthorImpactReportResponse {

    /** Report type identifier for frontend routing. */
    @Builder.Default
    private String reportType = "AUTHOR_IMPACT_REPORT";

    /** Report title in Vietnamese: "Hồ sơ năng lực học thuật: [Author Name]" */
    private String reportTitle;

    /** Author's full name. */
    private String authorName;

    /** Author's current institutional affiliation. */
    private String affiliation;

    /** Total number of published papers. */
    private Integer totalPapers;

    /**
     * h-index — measures both productivity and citation impact.
     * An h-index of 20 means the author has at least 20 papers each cited at least 20 times.
     */
    @Builder.Default
    private Integer hIndex = 0;

    /**
     * Activity status: "Đang sung sức" (actively publishing) or "Đã dừng nghiên cứu" (inactive).
     */
    private String status;

    /** AI-style insight text in Vietnamese summarizing the author's expertise. */
    private String insight;

    /** The research field this author is most active in (from PaperAuthor aggregation). */
    private String topField;

    /** Top collaborators (co-authors) with name, affiliation, and collaboration count. */
    private List<Collaborator> topCollaborators;

    // ── Nested DTO ──

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Collaborator {
        /** Co-author's name. */
        private String name;
        /** Co-author's last known institution. */
        private String affiliation;
        /** Number of papers co-authored together. */
        private Integer collaborationCount;
    }
}
