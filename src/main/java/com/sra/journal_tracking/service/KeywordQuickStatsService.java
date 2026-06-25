package com.sra.journal_tracking.service;

import com.sra.journal_tracking.dto.paper.KeywordQuickStatsResponse;
import com.sra.journal_tracking.dto.paper.PaperDetailResponseDTO;
import com.sra.journal_tracking.dto.paper.RelatedKeywordResponse;

import java.util.List;

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

    /**
     * Discover keywords that frequently co-occur with the given keyword
     * in recent papers (last 2 years). Uses Neo4j graph traversal to find
     * "satellite keywords" — helps users discover research niches.
     *
     * @param keyword the search keyword (raw user input)
     * @return top 10 co-occurring keywords ranked by frequency, with growth rates
     */
    List<RelatedKeywordResponse> getRelatedTrends(String keyword);

    /**
     * Get the top 5 most-cited (influential) papers for a keyword.
     * Uses Neo4j for paper discovery, then SQL for citation-based ranking.
     * These are the "foundation papers" anyone new to the topic should read first.
     *
     * @param keyword the search keyword (raw user input)
     * @return top 5 papers sorted by citation count descending
     */
    List<PaperDetailResponseDTO> getTopInfluentialPapers(String keyword);
}
