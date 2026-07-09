package com.sra.journal_tracking.service;

import com.sra.journal_tracking.dto.sync.OpenAlexResponseDTO;
import com.sra.journal_tracking.entity.jpa.SyncLog;

public interface DataSyncService {
    SyncLog syncFromSemanticScholar(String query, int limit);
    SyncLog syncFromOpenAlex(String query, int limit);
    void syncFromOpenAlexAsync(String query, int limit);
    void triggerManualSyncAsync(String sourceName, String query, int limit, Integer yearFrom, Integer yearTo);

    // ── Year range variants ──
    SyncLog syncFromOpenAlex(String query, int limit, Integer yearFrom, Integer yearTo);
    SyncLog syncFromSemanticScholar(String query, int limit, Integer yearFrom, Integer yearTo);
    SyncLog syncFromArxiv(String query, int limit, Integer yearFrom, Integer yearTo);
    SyncLog syncFromArxiv(String query, int limit);
    SyncLog syncFromCore(String query, int limit, Integer yearFrom, Integer yearTo);
    SyncLog syncFromCore(String query, int limit);

    /**
     * Sync papers from OpenAlex filtered by a specific author ID.
     * Used as fallback when local author search returns empty results.
     *
     * @param openAlexAuthorId OpenAlex author ID (e.g. "https://openalex.org/A5023888391")
     * @param authorName       human-readable author name for logging
     * @param limit            max papers to fetch
     * @return SyncLog with sync results
     */
    SyncLog syncPapersFromOpenAlexByAuthor(String openAlexAuthorId, String authorName, int limit);

    /**
     * Save a single OpenAlex work to the local database.
     * Used to ensure papers returned by search fallback are available for detail view.
     *
     * @param work the OpenAlex work DTO
     */
    void saveSingleWorkFromOpenAlex(OpenAlexResponseDTO.OpenAlexWorkDTO work);

    /**
     * Get comprehensive database statistics for admin dashboard.
     */
    com.sra.journal_tracking.dto.dashboard.DatabaseStatsResponse getDatabaseStats();

    /**
     * Delete ALL papers from SQL Server and Neo4j.
     * Clears paper-related data (keywords, authors, journals, fields)
     * and resets the Neo4j graph. Useful for cleaning mock/test data.
     *
     * @return Map with deleted counts (deletedPapers, deletedKeywords, deletedAuthors, neo4jCleared)
     */
    java.util.Map<String, Object> clearAllPapers();

    /**
     * Bulk sync papers from OpenAlex for multiple keywords (async).
     * Uses pagination to maximize paper count.
     */
    java.util.Map<String, Object> bulkSyncFromOpenAlex(java.util.List<String> keywords, int papersPerKeyword, Integer yearFrom, Integer yearTo);

    /**
     * Bulk sync with custom mailto email (for team members to use their own polite pool quota).
     */
    java.util.Map<String, Object> bulkSyncFromOpenAlex(java.util.List<String> keywords, int papersPerKeyword, Integer yearFrom, Integer yearTo, String mailto);

    /**
     * Bulk sync with custom API key and progress tracking via taskId.
     * Updates {@link BulkSyncProgressTracker} after each keyword completes.
     */
    java.util.Map<String, Object> bulkSyncFromOpenAlex(String taskId, java.util.List<String> keywords, int papersPerKeyword, Integer yearFrom, Integer yearTo, String mailto, String apiKey);

    /**
     * Async version of bulkSyncFromOpenAlex with progress tracking.
     * Updates {@link BulkSyncProgressTracker} as each keyword completes.
     *
     * @param taskId unique task identifier for progress tracking
     * @param mailto optional email for OpenAlex polite pool
     * @param apiKey optional OpenAlex API key
     */
    void bulkSyncFromOpenAlexAsync(String taskId, java.util.List<String> keywords, int papersPerKeyword, Integer yearFrom, Integer yearTo, String mailto, String apiKey);

    /**
     * Bulk (deep) sync papers from CORE API for multiple keywords.
     * Uses offset-based pagination to maximize paper count.
     * Supports custom API key override (falls back to {@code app.core-api-key} config).
     * Updates {@link BulkSyncProgressTracker} after each keyword completes.
     *
     * @param taskId           unique task identifier for progress tracking
     * @param keywords         list of search keywords
     * @param papersPerKeyword max papers to insert per keyword
     * @param yearFrom         optional start year filter
     * @param yearTo           optional end year filter
     * @param apiKey           optional CORE API key (overrides the configured key)
     * @return Map with totalKeywords, totalFetched, totalInserted, yearRange, keywordStats
     */
    java.util.Map<String, Object> bulkSyncFromCore(String taskId, java.util.List<String> keywords,
                                                    int papersPerKeyword, Integer yearFrom, Integer yearTo,
                                                    String apiKey);

    /**
     * Async version of {@link #bulkSyncFromCore} — runs in background.
     * Updates {@link BulkSyncProgressTracker} as each keyword completes.
     * Use {@code GET /api/v1/admin/sync/bulk/{taskId}/progress} to track.
     *
     * @param taskId unique task identifier for progress tracking
     */
    void bulkSyncFromCoreAsync(String taskId, java.util.List<String> keywords,
                               int papersPerKeyword, Integer yearFrom, Integer yearTo, String apiKey);

    /**
     * Bulk (deep) sync papers from Semantic Scholar for multiple keywords.
     * Uses offset-based pagination. Updates {@link BulkSyncProgressTracker} per page.
     */
    java.util.Map<String, Object> bulkSyncFromSemanticScholar(String taskId, java.util.List<String> keywords,
                                                               int papersPerKeyword, Integer yearFrom, Integer yearTo,
                                                               String apiKey);

    /**
     * Async version of {@link #bulkSyncFromSemanticScholar} — runs in background.
     */
    void bulkSyncFromSemanticScholarAsync(String taskId, java.util.List<String> keywords,
                                          int papersPerKeyword, Integer yearFrom, Integer yearTo,
                                          String apiKey);

    /**
     * Bulk (deep) sync papers from arXiv for multiple keywords.
     * Uses start/max_results pagination on XML Atom feed. Updates {@link BulkSyncProgressTracker} per page.
     */
    java.util.Map<String, Object> bulkSyncFromArxiv(String taskId, java.util.List<String> keywords,
                                                     int papersPerKeyword, Integer yearFrom, Integer yearTo);

    /**
     * Async version of {@link #bulkSyncFromArxiv} — runs in background.
     */
    void bulkSyncFromArxivAsync(String taskId, java.util.List<String> keywords,
                                int papersPerKeyword, Integer yearFrom, Integer yearTo);

    /**
     * Re-extract keywords for all papers in the database.
     * Useful after upgrading keyword extraction algorithm.
     *
     * @return Map with processed/total counts
     */
    java.util.Map<String, Object> reExtractKeywords();

    /**
     * Get papers recently added by sync (last N hours, paginated).
     * Useful for admin to see what was just imported.
     */
    org.springframework.data.domain.Page<com.sra.journal_tracking.entity.jpa.ResearchPaper> getRecentSyncedPapers(int hours, int page, int size);

    /**
     * Backfill author metrics (hIndex, totalCitations, i10Index, worksCount)
     * from OpenAlex for authors that have an externalAuthorId but no metrics yet.
     *
     * @param limit max authors to process (0 = unlimited)
     * @return Map with totalProcessed, updated, skipped, errors
     */
    java.util.Map<String, Object> backfillAuthorMetrics(int limit);
}
