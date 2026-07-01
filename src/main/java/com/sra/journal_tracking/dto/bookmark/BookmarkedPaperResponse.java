package com.sra.journal_tracking.dto.bookmark;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Bookmarked paper entry for the dashboard "Tài liệu đã lưu" (Bookmarked Papers) card.
 * Shows the 5 most recently bookmarked papers for quick access.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BookmarkedPaperResponse {

    /** Bookmark record ID. */
    private UUID bookmarkId;

    /** Paper ID — navigates to paper detail page. */
    private UUID paperId;

    /** Paper title. */
    private String paperTitle;

    /** Journal name (may be null if paper has no journal). */
    private String journalName;

    /** Number of citations. */
    private Integer citationCount;

    /** Publication year. */
    private Short pubYear;

    /** When the user bookmarked this paper. */
    private LocalDateTime bookmarkedAt;
}
