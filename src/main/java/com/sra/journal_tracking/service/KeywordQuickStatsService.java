package com.sra.journal_tracking.service;

import com.sra.journal_tracking.dto.paper.KeywordQuickStatsResponse;

/**
 * Service for computing quick statistics about a keyword — paper count,
 * total citations, YoY growth rate, and average citations per paper.
 */
public interface KeywordQuickStatsService {

    /**
     * Compute quick stats for a given keyword using Neo4j graph + SQL aggregation.
     *
     * @param keyword the search keyword (raw user input)
     * @return aggregated stats for the keyword
     */
    KeywordQuickStatsResponse getStats(String keyword);
}
