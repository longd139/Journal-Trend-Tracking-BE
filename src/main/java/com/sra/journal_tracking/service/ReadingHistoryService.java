package com.sra.journal_tracking.service;

import com.sra.journal_tracking.dto.history.ReadingHistoryResponse;

import java.util.List;
import java.util.UUID;

public interface ReadingHistoryService {

    /**
     * Get the user's most recently viewed papers.
     *
     * @param userEmail authenticated user's email
     * @param limit     max entries (default 20)
     * @return deduplicated list, newest first
     */
    List<ReadingHistoryResponse> getRecentViews(String userEmail, int limit);

    /**
     * Record that a user viewed a paper. Called from paper detail endpoint.
     * Runs in a separate transaction + async to not block the response.
     * If the paper does not exist in the local DB, a minimal stub is created.
     *
     * @param userEmail authenticated user's email
     * @param paperId   the paper being viewed
     * @param title     paper title (used to create stub if paper not in DB)
     * @param doi       paper DOI (used to create stub if paper not in DB)
     * @param pubYear   publication year (used to create stub if paper not in DB)
     */
    void recordView(String userEmail, UUID paperId, String title, String doi, Integer pubYear);
}
