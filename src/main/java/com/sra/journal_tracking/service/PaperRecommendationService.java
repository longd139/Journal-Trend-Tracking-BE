package com.sra.journal_tracking.service;

import com.sra.journal_tracking.dto.recommendation.RecommendationResultDTO;

import java.util.UUID;

/**
 * Service for generating personalized paper recommendations and finding similar papers.
 */
public interface PaperRecommendationService {

    /**
     * Get personalized paper recommendations for a user based on their
     * search history, bookmarks, and follows (hybrid content-based + collaborative filtering).
     *
     * @param userEmail the authenticated user's email
     * @param page      zero-based page index
     * @param size      page size (1-50)
     * @return paginated recommendations with reason labels
     */
    RecommendationResultDTO getPersonalizedRecommendations(String userEmail, int page, int size);

    /**
     * Find papers similar to a given paper based on shared keywords and research field.
     *
     * @param userEmail the authenticated user's email
     * @param paperId   the source paper ID
     * @param page      zero-based page index
     * @param size      page size (1-50)
     * @return paginated similar papers with similarity metadata
     */
    RecommendationResultDTO getSimilarPapers(String userEmail, UUID paperId, int page, int size);

    /**
     * Evict all cached recommendations for a user.
     * Called after a new search is recorded, a bookmark is added/removed,
     * or a follow target changes.
     *
     * @param userEmail the user whose cache should be cleared
     */
    void evictUserCache(String userEmail);
}
