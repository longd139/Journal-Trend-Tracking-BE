package com.sra.journal_tracking.service;

import com.sra.journal_tracking.dto.journal.JournalCategoryResponse;

import java.util.List;
import java.util.UUID;

public interface JournalService {
    /**
     * Get all top-level research fields with their top-tier journals.
     * Used when the journal search page loads without a keyword.
     */
    List<JournalCategoryResponse> getJournalCategories();

    /**
     * Get top-tier journals for a specific research field.
     */
    JournalCategoryResponse getTopJournalsByField(UUID fieldId);
}
