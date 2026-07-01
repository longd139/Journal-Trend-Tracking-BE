package com.sra.journal_tracking.dto.follow;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Watchlist item for the dashboard "Radar Theo dõi" (My Watchlist) card.
 * Shows a followed keyword, topic, or journal with the number of new papers this week.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WatchlistItemResponse {

    /** Follow record ID. */
    private UUID followId;

    /** Target type: KEYWORD, JOURNAL, or TOPIC. */
    private String targetType;

    /** Display name of the followed target. */
    private String targetName;

    /** Target entity ID. */
    private UUID targetId;

    /** Total number of papers for this target (all time). */
    private Long totalPapers;

    /** Number of new papers created in the last 7 days. */
    private Long newPapersThisWeek;

    /** Whether notification is enabled for this follow. */
    private Boolean notifyEnabled;
}
