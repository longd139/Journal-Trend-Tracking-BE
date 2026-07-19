package com.sra.journal_tracking.dto.gap;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Fuzzy match suggestion for a single unmatched user term.
 * Contains top-3 closest Neo4j keywords with similarity scores.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FuzzyMatchSuggestion {

    private String originalTerm;
    private List<FuzzyCandidate> candidates;

    public FuzzyMatchSuggestion() {}

    public FuzzyMatchSuggestion(String originalTerm, List<FuzzyCandidate> candidates) {
        this.originalTerm = originalTerm;
        this.candidates = candidates;
    }

    public String getOriginalTerm() { return originalTerm; }
    public void setOriginalTerm(String originalTerm) { this.originalTerm = originalTerm; }

    public List<FuzzyCandidate> getCandidates() { return candidates; }
    public void setCandidates(List<FuzzyCandidate> candidates) { this.candidates = candidates; }

    // ── Inner type ──

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FuzzyCandidate {
        private String keywordText;
        private String normalizedText;
        private double similarity;   // 0.0 – 1.0
        private int paperCount;

        public FuzzyCandidate() {}
        public FuzzyCandidate(String keywordText, String normalizedText, double similarity, int paperCount) {
            this.keywordText = keywordText;
            this.normalizedText = normalizedText;
            this.similarity = similarity;
            this.paperCount = paperCount;
        }

        public String getKeywordText() { return keywordText; }
        public void setKeywordText(String keywordText) { this.keywordText = keywordText; }
        public String getNormalizedText() { return normalizedText; }
        public void setNormalizedText(String normalizedText) { this.normalizedText = normalizedText; }
        public double getSimilarity() { return similarity; }
        public void setSimilarity(double similarity) { this.similarity = similarity; }
        public int getPaperCount() { return paperCount; }
        public void setPaperCount(int paperCount) { this.paperCount = paperCount; }
    }
}
