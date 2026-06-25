package com.sra.journal_tracking.service;

import com.sra.journal_tracking.dto.journal.JournalAuthorResponse;
import com.sra.journal_tracking.dto.journal.JournalQuickStatsResponse;
import com.sra.journal_tracking.dto.journal.JournalTimelineResponse;
import com.sra.journal_tracking.dto.paper.PaperDetailResponseDTO;

import java.util.List;

/**
 * Service for journal prestige KPIs: Impact Factor, Quartile,
 * total publications, total citations, yearly timeline,
 * top papers, and top authors.
 */
public interface JournalQuickStatsService {

    /** Search for a journal by name and return its KPIs. */
    JournalQuickStatsResponse getStats(String journalName);

    /** Yearly timeline of journal impact and volume (last 10 years). */
    JournalTimelineResponse getTimeline(String journalName);

    /** Top 5 most-cited papers in a journal. */
    List<PaperDetailResponseDTO> getTopPapers(String journalName);

    /** Top 10 most frequent authors in a journal. */
    List<JournalAuthorResponse> getTopAuthors(String journalName);
}
