package com.sra.journal_tracking.dto.journal;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A top contributing author in a journal — "Ai đang gánh uy tín cho tạp chí này?".
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JournalAuthorResponse {

    /** Author full name. */
    private String authorName;

    /** OpenAlex author ID (for linking to author search page). */
    private String openAlexId;

    /** Number of papers published by this author in this journal. */
    private Long paperCount;

    /** Total citations accumulated by this author's papers in this journal. */
    private Long totalCitations;

    /** Average citations per paper for this author in this journal. */
    private Double avgCitationsPerPaper;
}
