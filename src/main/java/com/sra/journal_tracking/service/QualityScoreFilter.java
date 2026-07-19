package com.sra.journal_tracking.service;

import com.sra.journal_tracking.entity.jpa.Journal;
import com.sra.journal_tracking.entity.jpa.ResearchPaper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Scores paper quality based on objective signals:
 * citation count, journal quartile, publication type,
 * open access availability, abstract completeness, and DOI presence.
 * <p>
 * Only papers with score >= MIN_QUALITY_SCORE (40) are kept
 * for enrichment and gap analysis.
 */
@Component
public class QualityScoreFilter {

    private static final Logger log = LoggerFactory.getLogger(QualityScoreFilter.class);

    public static final int MIN_QUALITY_SCORE = 40;
    public static final int MAX_SCORE = 100;

    /**
     * Calculate quality score for a paper.
     *
     * @param paper   the research paper entity
     * @param journal the associated journal (can be null)
     * @param isOpenAccess whether the paper is open access
     * @param hasSubstantialAbstract whether abstract has > 200 words
     * @return quality score 0–100
     */
    public int calculateScore(ResearchPaper paper, Journal journal,
                               boolean isOpenAccess, boolean hasSubstantialAbstract) {
        int score = 0;

        // Citation count (most important signal)
        int citations = paper.getCitationCount() != null ? paper.getCitationCount() : 0;
        if (citations >= 50) score += 30;
        else if (citations >= 20) score += 20;
        else if (citations >= 5) score += 10;

        // Journal quartile (from SCImago data)
        if (journal != null && journal.getQuartile() != null) {
            String q = journal.getQuartile().toUpperCase().trim();
            if ("Q1".equals(q)) score += 25;
            else if ("Q2".equals(q)) score += 15;
            else if ("Q3".equals(q)) score += 5;
        }

        // Publication type — prefer journal articles
        String type = paper.getType();
        if ("journal-article".equalsIgnoreCase(type)) score += 15;
        else if ("proceedings-article".equalsIgnoreCase(type)) score += 10;

        // Open Access — enables full-text enrichment
        if (isOpenAccess) score += 15;

        // Has substantial abstract — needed for AI extraction
        if (hasSubstantialAbstract) score += 15;

        // Has DOI — verifiable paper
        if (paper.getDoi() != null && !paper.getDoi().isBlank()) score += 5;

        return Math.min(score, MAX_SCORE);
    }

    /**
     * Check if a paper meets the minimum quality threshold.
     */
    public boolean isQualityPaper(int score) {
        return score >= MIN_QUALITY_SCORE;
    }

    /**
     * Get a human-readable quality label.
     */
    public String getQualityLabel(int score) {
        if (score >= 70) return "INFLUENTIAL";
        if (score >= 40) return "SOLID";
        return "LOW";
    }

    /**
     * Check if an abstract has substantial content for AI extraction (> 200 words).
     */
    public boolean hasSubstantialAbstract(String abstractText) {
        if (abstractText == null || abstractText.isBlank()) return false;
        int wordCount = abstractText.trim().split("\\s+").length;
        return wordCount > 200;
    }

    /**
     * Log a summary for a batch of scored papers.
     */
    public void logBatchSummary(java.util.List<Integer> scores) {
        long influential = scores.stream().filter(s -> s >= 70).count();
        long solid = scores.stream().filter(s -> s >= 40 && s < 70).count();
        long low = scores.stream().filter(s -> s < 40).count();

        log.info("Quality Score Summary: {} influential (>=70), {} solid (40-69), {} low (<40) — "
                + "keeping {} papers (score >= {})",
                influential, solid, low, influential + solid, MIN_QUALITY_SCORE);
    }
}
