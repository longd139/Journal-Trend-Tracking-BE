package com.sra.journal_tracking.service;

import com.sra.journal_tracking.entity.jpa.SyncLog;

public interface DataSyncService {
    SyncLog syncFromSemanticScholar(String query, int limit);
    SyncLog syncFromOpenAlex(String query, int limit);
}
