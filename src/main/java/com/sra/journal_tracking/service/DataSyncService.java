package com.sra.journal_tracking.service;

import com.sra.journal_tracking.entity.jpa.AutoSyncKeyword;
import com.sra.journal_tracking.entity.jpa.ResearchPaper;
import com.sra.journal_tracking.entity.jpa.SyncLog;

import java.util.List;
import java.util.UUID;

public interface DataSyncService {
    SyncLog syncFromSemanticScholar(String query, int limit);
    SyncLog syncFromOpenAlex(String query, int limit);

    /**
     * Sync papers from OpenAlex and return the saved ResearchPaper entities directly.
     * Avoids a second SQL LIKE query after sync — callers get the data immediately.
     *
     * @param query search keyword
     * @param limit max papers to fetch
     * @return list of newly saved ResearchPaper entities (empty list if none or error)
     */
    List<ResearchPaper> syncFromOpenAlexAndReturnPapers(String query, int limit);

    /**
     * Backfill: đồng bộ toàn bộ keyword từ SQL PAPER_KEYWORD sang Neo4j.
     * Dùng khi Neo4j chưa có dữ liệu keyword (paper cũ, migrate, reset).
     *
     * @param batchSize số lượng paper xử lý mỗi batch
     * @return tổng số paper đã đồng bộ
     */
    int backfillKeywordsToNeo4j(int batchSize);

    // ── Auto-sync keyword management ──

    AutoSyncKeyword addAutoSyncKeyword(String keyword, int intervalMinutes);
    List<AutoSyncKeyword> getAutoSyncKeywords();
    AutoSyncKeyword updateAutoSyncKeyword(UUID id, String keyword, Integer intervalMinutes, Boolean enabled);
    void deleteAutoSyncKeyword(UUID id);
    void runAutoSync();
}
