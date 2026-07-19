package com.sra.journal_tracking.dto.gap;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Result of matching a user's research idea against available Neo4j keywords.
 * Supports 4 match levels for progressive fallback:
 * FULL → PARTIAL → FUZZY_ONLY → NONE.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KeywordMatchResult {

    public enum MatchLevel {
        /** ≥2 keywords matched exactly → proceed to gap suggestions normally */
        FULL,
        /** 1 keyword matched → can still suggest but limited */
        PARTIAL,
        /** No exact match, but fuzzy candidates exist → ask user to confirm */
        FUZZY_ONLY,
        /** Nothing at all → show guided discovery landscape */
        NONE
    }

    private List<KeywordExactMatch> exactMatches;
    private List<FuzzyMatchSuggestion> fuzzySuggestions;
    private List<String> unmatchedTerms;
    private List<KeywordLandscapeItem> availableLandscape;
    private MatchLevel matchLevel;
    private String guidanceMessage;

    public KeywordMatchResult() {}

    public List<KeywordExactMatch> getExactMatches() { return exactMatches; }
    public void setExactMatches(List<KeywordExactMatch> exactMatches) { this.exactMatches = exactMatches; }

    public List<FuzzyMatchSuggestion> getFuzzySuggestions() { return fuzzySuggestions; }
    public void setFuzzySuggestions(List<FuzzyMatchSuggestion> fuzzySuggestions) { this.fuzzySuggestions = fuzzySuggestions; }

    public List<String> getUnmatchedTerms() { return unmatchedTerms; }
    public void setUnmatchedTerms(List<String> unmatchedTerms) { this.unmatchedTerms = unmatchedTerms; }

    public List<KeywordLandscapeItem> getAvailableLandscape() { return availableLandscape; }
    public void setAvailableLandscape(List<KeywordLandscapeItem> availableLandscape) { this.availableLandscape = availableLandscape; }

    public MatchLevel getMatchLevel() { return matchLevel; }
    public void setMatchLevel(MatchLevel matchLevel) { this.matchLevel = matchLevel; }

    public String getGuidanceMessage() { return guidanceMessage; }
    public void setGuidanceMessage(String guidanceMessage) { this.guidanceMessage = guidanceMessage; }

    // ── Inner types ──

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class KeywordExactMatch {
        private String text;
        private String normalizedText;
        private int paperCount;

        public KeywordExactMatch() {}
        public KeywordExactMatch(String text, String normalizedText, int paperCount) {
            this.text = text;
            this.normalizedText = normalizedText;
            this.paperCount = paperCount;
        }

        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
        public String getNormalizedText() { return normalizedText; }
        public void setNormalizedText(String normalizedText) { this.normalizedText = normalizedText; }
        public int getPaperCount() { return paperCount; }
        public void setPaperCount(int paperCount) { this.paperCount = paperCount; }
    }
}
