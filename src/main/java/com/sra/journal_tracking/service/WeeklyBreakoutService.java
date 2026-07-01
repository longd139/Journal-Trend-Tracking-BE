package com.sra.journal_tracking.service;

import com.sra.journal_tracking.dto.trends.WeeklyBreakoutResponse;

import java.util.List;

/**
 * Service for computing weekly breakout / trending research topics
 * with 6-month citation sparkline data and growth classification.
 */
public interface WeeklyBreakoutService {

    /**
     * Returns the top 5 breakout research topics with sparkline data,
     * growth labels, total paper counts, and growth rates.
     *
     * @return list of up to 5 breakout topic cards
     */
    List<WeeklyBreakoutResponse> getWeeklyBreakoutTopics();
}
