package com.sra.journal_tracking.service;

import java.util.List;
import java.util.UUID;

import com.sra.journal_tracking.dto.follow.FollowRequest;
import com.sra.journal_tracking.dto.follow.FollowResponse;
import com.sra.journal_tracking.dto.follow.QuickInsightResponse;
import com.sra.journal_tracking.dto.follow.WatchlistItemResponse;

public interface FollowService {

    FollowResponse addFollow(String email, FollowRequest request);

    List<FollowResponse> getMyFollows(String email);

    FollowResponse updateFollow(String email, UUID followId, boolean notifyEnabled);

    void deleteFollow(String email, UUID followId);

    /**
     * Get the current user's watchlist — their followed keywords, journals, and topics
     * enriched with total paper counts and new-papers-this-week counts.
     *
     * @param email the authenticated user's email
     * @return list of WatchlistItemResponse sorted by newPapersThisWeek descending
     */
    List<WatchlistItemResponse> getMyWatchlist(String email);

    /**
     * Generate a personalized quick analytical insight for the dashboard
     * "Báo cáo nhanh của bạn" card. Analyzes the user's followed keywords
     * and journals to produce a Vietnamese natural-language summary.
     *
     * @param email the authenticated user's email
     * @return QuickInsightResponse with auto-generated insight text
     */
    QuickInsightResponse getQuickInsight(String email);
}
