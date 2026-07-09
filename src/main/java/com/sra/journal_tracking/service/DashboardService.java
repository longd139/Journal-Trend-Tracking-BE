package com.sra.journal_tracking.service;

import java.util.UUID;

import com.sra.journal_tracking.dto.dashboard.OverviewStatsResponse;
import com.sra.journal_tracking.dto.dashboard.TotalPapersResponse;

/**
 * Service interface for dashboard and overview statistics.
 */
public interface DashboardService {

    /**
     * Get overview statistics for the landing page dashboard.
     *
     * When authorId is null: returns system-wide stats (4 cards).
     * When authorId is provided: returns author-specific stats (4 cards):
     *   1. Total Papers — papers published by this author
     *   2. Total Citations — citations received by this author
     *   3. h-index — academic impact indicator
     *   4. Co-authors — unique collaborators in research network
     *
     * @param authorId optional — if provided, stats are scoped to this author
     * @return OverviewStatsResponse with real data from database
     */
    OverviewStatsResponse getOverviewStats(UUID authorId);

    /**
     * Get total number of research papers in the system.
     * Simple count query — no pagination, no auth required.
     *
     * @return TotalPapersResponse with count from database
     */
    TotalPapersResponse getTotalPapers();
}
