package com.sra.journal_tracking.service;

import com.sra.journal_tracking.dto.author.SuggestedAuthorResponse;

import java.util.List;

public interface AuthorSuggestionService {
    /**
     * Get suggested authors for zero-state display.
     * Returns top authors by citation count with their primary research field.
     * Cached per day.
     */
    List<SuggestedAuthorResponse> getSuggestedAuthors();
}
