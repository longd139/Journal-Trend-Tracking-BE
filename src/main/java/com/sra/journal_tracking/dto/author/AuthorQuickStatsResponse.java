package com.sra.journal_tracking.dto.author;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthorQuickStatsResponse {

    /**
     * Author's full name (from OpenAlex display_name)
     */
    private String fullName;

    /**
     * Academic title/degree — may be null if not available from OpenAlex
     */
    private String academicTitle;

    /**
     * Current institutional affiliation (from last_known_institution)
     */
    private String currentAffiliation;

    /**
     * Total number of published papers (works_count)
     */
    private Integer totalPapers;

    /**
     * Total citation count (cited_by_count)
     */
    private Integer totalCitations;

    /**
     * h-index — measures both productivity and citation impact.
     * h-index = 20 means the author has at least 20 papers, each cited at least 20 times.
     */
    private Integer hIndex;

    /**
     * i10-index — number of papers with at least 10 citations
     */
    private Integer i10Index;

    /**
     * 2-year mean citedness from summary_stats
     */
    private Double twoYearMeanCitedness;

    /**
     * ORCID identifier (e.g. "0000-0001-2345-6789")
     */
    private String orcid;

    /**
     * OpenAlex author ID URL (e.g. "https://openalex.org/A5023888391")
     */
    private String openAlexId;
}
