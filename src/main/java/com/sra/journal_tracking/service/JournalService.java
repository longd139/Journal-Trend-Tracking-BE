package com.sra.journal_tracking.service;

import com.sra.journal_tracking.dto.journal.JournalCategoryResponse;
import com.sra.journal_tracking.dto.journal.TopJournalDTO;

import java.util.List;
import java.util.UUID;

public interface JournalService {
    List<JournalCategoryResponse> getJournalCategories();
    JournalCategoryResponse getTopJournalsByField(UUID fieldId);
    List<TopJournalDTO> searchJournals(String query, int size);
}
