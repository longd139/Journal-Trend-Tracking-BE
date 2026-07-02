package com.sra.journal_tracking.service;

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
     * Bulk sync with custom API key (for team members to use their own OpenAlex quota).
     * Falls back to server-wide api key if not provided.
     */
    java.util.Map<String, Object> bulkSyncFromOpenAlex(java.util.List<String> keywords, int papersPerKeyword, Integer yearFrom, Integer yearTo, String mailto, String apiKey);

    /**
     * Async version of bulkSyncFromOpenAlex with progress tracking.
     * Updates {@link BulkSyncProgressTracker} as each keyword completes.
     *
     * @param taskId unique task identifier for progress tracking
     */
    void bulkSyncFromOpenAlexAsync(String taskId, java.util.List<String> keywords, int papersPerKeyword, Integer yearFrom, Integer yearTo);

    /**
     * Re-extract keywords for all papers in the database.
     * Useful after upgrading keyword extraction algorithm.
     *
     * @return Map with processed/total counts
     */
    java.util.Map<String, Object> reExtractKeywords();
}
