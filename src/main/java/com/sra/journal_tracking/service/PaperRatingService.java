package com.sra.journal_tracking.service;

import com.sra.journal_tracking.dto.rating.RatingResponse;

import java.util.UUID;

public interface PaperRatingService {
    /** Rate a paper (1-5). Upserts if user already rated. */
    RatingResponse ratePaper(UUID paperId, String userEmail, int score);

    /** Get the current user's rating + average for a paper. */
    RatingResponse getRating(UUID paperId, String userEmail);
}
