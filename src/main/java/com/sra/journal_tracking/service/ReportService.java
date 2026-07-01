package com.sra.journal_tracking.service;

import com.sra.journal_tracking.dto.report.AuthorImpactReportResponse;
import com.sra.journal_tracking.dto.report.JournalQualityReportResponse;
import com.sra.journal_tracking.dto.report.KeywordTrendReportResponse;
import com.sra.journal_tracking.dto.report.TrendingTopicResponse;

import java.util.List;

/**
 * Report generation service — produces analytical reports for keywords, authors, and journals.
 * All methods are read-only and reuse existing data from SQL Server and Neo4j.
 */
public interface ReportService {

    /**
     * Generate a keyword trend report analyzing whether a research topic is heating up or cooling down.
     *
     * @param keyword the search keyword to analyze
     * @return KeywordTrendReportResponse with total papers, YoY growth, status, insight, and related keywords
     */
    KeywordTrendReportResponse getKeywordTrendReport(String keyword);

    /**
     * Generate an author impact report assessing whether an author is a top expert in their field.
     *
     * @param authorName the author name to analyze
     * @return AuthorImpactReportResponse with h-index, total papers, activity status, insight, and collaborators
     */
    AuthorImpactReportResponse getAuthorImpactReport(String authorName);

    /**
     * Generate a journal quality report evaluating a journal's prestige and submission suitability.
     *
     * @param journalName the journal name to analyze
     * @return JournalQualityReportResponse with quartile, impact factor, editorial taste, and insight
     */
    JournalQualityReportResponse getJournalQualityReport(String journalName);

    /**
     * Get the top 5 trending research topics ranked by year-over-year growth rate.
     * Uses a candidate-pool approach: fetches the most-connected keywords from Neo4j,
     * computes YoY growth for each, and returns the top 5 by growth rate descending.
     *
     * @return list of up to 5 TrendingTopicResponse, each with keyword, growth rate, status, insight, and sparkline
     */
    List<TrendingTopicResponse> getTrendingTopics();
}
