package com.sra.journal_tracking.service;

import com.sra.journal_tracking.dto.report.AuthorImpactReportResponse;
import com.sra.journal_tracking.dto.report.JournalQualityReportResponse;
import com.sra.journal_tracking.dto.report.KeywordTrendHistoryItem;
import com.sra.journal_tracking.dto.report.KeywordTrendReportResponse;

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
     * @return KeywordTrendReportResponse with publication/citation trends, co-occurring keywords, and top journals
     */
    KeywordTrendReportResponse getKeywordTrendReport(String keyword);

    /**
     * Retrieve a previously cached keyword trend report from the database.
     * Falls back to generating a fresh report if no cache entry exists.
     *
     * @param keyword the search keyword
     * @return KeywordTrendReportResponse deserialized from the cached JSON
     */
    KeywordTrendReportResponse getCachedKeywordTrendReport(String keyword);

    /**
     * Get all previously generated keyword trend reports.
     *
     * @return list of lightweight history items with keyword and timestamps, newest first
     */
    List<KeywordTrendHistoryItem> getKeywordTrendHistory();

    /**
     * Delete a cached keyword trend report from the database.
     *
     * @param keyword the keyword whose cached report should be deleted
     */
    void deleteCachedKeywordTrendReport(String keyword);

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
}
