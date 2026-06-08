package com.sra.journal_tracking.service;

import com.sra.journal_tracking.dto.dashboard.OverviewStatsResponse;
import com.sra.journal_tracking.dto.dashboard.TotalPapersResponse;

/**
 * Service interface for dashboard and overview statistics.
 */
public interface DashboardService {

    /**
     * Get overview statistics for the landing page dashboard.
     * Returns 4 cards: Papers Tracked, Total Citations,
     * Top Trending Now, and Top Growth Topic.
     *
     * @return OverviewStatsResponse with real data from database
     */
    OverviewStatsResponse getOverviewStats();

    /**
     * Get total number of research papers in the system.
     * Simple count query — no pagination, no auth required.
     *
     * @return TotalPapersResponse with count from database
     */
    TotalPapersResponse getTotalPapers();
}
