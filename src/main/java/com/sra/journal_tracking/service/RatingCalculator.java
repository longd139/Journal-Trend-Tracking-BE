package com.sra.journal_tracking.service;

import com.sra.journal_tracking.entity.jpa.Journal;
import lombok.extern.slf4j.Slf4j;

/**
 * Computes a composite paper rating (0.0–5.0) based on three weighted factors:
 *
 *   1. Journal Prestige (40%) — derived from quartile ranking (Q1–Q4)
 *   2. Citation Impact (35%) — log-scaled citation count
 *   3. User Rating    (25%) — average user score from PaperRating table
 *
 * This provides a defensible, multi-dimensional quality score
 * suitable for academic paper evaluation.
 */
@Slf4j
public final class RatingCalculator {

    private static final double MAX_CITATION_LOG = Math.log10(100_000 + 1); // ~5.0

    private RatingCalculator() { /* utility class */ }

    /**
     * Compute composite rating for a paper.
     *
     * @param journal      the paper's journal (may be null)
     * @param citationCount total citations (may be null)
     * @param avgUserRating average user rating from DB (may be null, 0.0–5.0)
     * @return composite score 0.0–5.0, rounded to 1 decimal
     */
    public static double compute(Journal journal, Integer citationCount, Double avgUserRating) {
        double quartileScore = quartileToScore(journal);
        double citationScore = citationsToScore(citationCount);
        double userScore = normalizeUserRating(avgUserRating);

        double composite = (quartileScore * 0.40)
                         + (citationScore * 0.35)
                         + (userScore    * 0.25);

        return Math.round(composite * 10.0) / 10.0;
    }

    // ── Factor extractors ──

    static double quartileToScore(Journal journal) {
        if (journal == null || journal.getQuartile() == null) return 1.0;
        return switch (journal.getQuartile().toUpperCase()) {
            case "Q1" -> 5.0;
            case "Q2" -> 4.0;
            case "Q3" -> 3.0;
            case "Q4" -> 2.0;
            default   -> 1.0;
        };
    }

    static double citationsToScore(Integer citationCount) {
        if (citationCount == null || citationCount <= 0) return 0.0;
        double logVal = Math.log10(citationCount + 1);
        // Normalize to 0–5 range using a cap of ~100k citations
        return Math.min(5.0, (logVal / MAX_CITATION_LOG) * 5.0);
    }

    static double normalizeUserRating(Double avgRating) {
        if (avgRating == null || avgRating <= 0.0) return 0.0;
        return Math.min(5.0, avgRating);
    }
}
