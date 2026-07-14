package com.sra.journal_tracking.service;

import com.sra.journal_tracking.dto.paper.KeywordQuickStatsResponse;
import com.sra.journal_tracking.dto.paper.PaperDetailResponseDTO;
import com.sra.journal_tracking.dto.paper.RelatedKeywordResponse;
import com.sra.journal_tracking.dto.search.KeywordComparisonRequest;
import com.sra.journal_tracking.dto.search.KeywordComparisonResponse;

import java.util.List;

/**
 * Service for computing quick statistics about a keyword — paper count,
 * total citations, YoY growth rate, and average citations per paper.
 */
public interface KeywordQuickStatsService {

    /**
     * Compute quick stats for a given keyword using OpenAlex API + Neo4j/SQL fallback.
     *
     * @param keyword the search keyword (raw user input)
     * @param pubYearFrom optional filter: publication year from (inclusive)
     * @param pubYearTo optional filter: publication year to (inclusive)
     * @param isOpenAccess optional filter: true = open access only, null = both
     * @return aggregated stats for the keyword
     */
    KeywordQuickStatsResponse getStats(String keyword, Integer pubYearFrom, Integer pubYearTo, Boolean isOpenAccess);

    /**
     * Discover keywords that frequently co-occur with the given keyword.
     * Uses Neo4j graph traversal to find "satellite keywords" — helps users discover research niches.
     *
     * @param keyword the search keyword (raw user input)
     * @param pubYearFrom optional filter: start year for co-occurrence window (default: currentYear - 2)
     * @param pubYearTo optional filter: end year for co-occurrence window (default: currentYear)
     * @return top 10 co-occurring keywords ranked by frequency, with growth rates
     */
    List<RelatedKeywordResponse> getRelatedTrends(String keyword, Integer pubYearFrom, Integer pubYearTo);

    /**
     * Get the top 5 most-cited (influential) papers for a keyword.
     * Uses Neo4j for paper discovery, then SQL for citation-based ranking.
     * These are the "foundation papers" anyone new to the topic should read first.
     *
     * @param keyword the search keyword (raw user input)
     * @param yearFrom optional: filter papers from this publication year (inclusive)
     * @param yearTo   optional: filter papers to this publication year (inclusive)
     * @return top 5 papers sorted by citation count descending
     */
    List<PaperDetailResponseDTO> getTopInfluentialPapers(String keyword, Integer yearFrom, Integer yearTo);

    /**
     * Compare multiple keywords side-by-side — returns paper count, citation count,
     * YoY growth rate, and peak year for each keyword. Used by the FE BarChart
     * component on the Analytics page.
     *
     * @param request DTO containing the list of keywords to compare (1–10)
     * @return comparison data for each keyword
     */
    KeywordComparisonResponse compareKeywords(KeywordComparisonRequest request);
}
