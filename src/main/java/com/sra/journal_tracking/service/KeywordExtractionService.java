package com.sra.journal_tracking.service;

import com.sra.journal_tracking.entity.jpa.Keyword;
import com.sra.journal_tracking.repository.jpa.KeywordRepository;
import com.sra.journal_tracking.repository.jpa.ResearchPaperRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Extracts important keywords from a paper's title and abstract
 * using n-gram candidate generation + TF-IDF scoring against the local corpus.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KeywordExtractionService {

    private static final int DEFAULT_MAX_KEYWORDS = 5;
    private static final double TITLE_BOOST = 2.0;
    private static final int MIN_TOKEN_LENGTH = 2;
    private static final int MAX_NGRAM_WORDS = 3;

    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "the", "and", "or", "but", "in", "on", "at", "to", "for",
            "of", "with", "by", "from", "is", "are", "was", "were", "be", "been",
            "being", "have", "has", "had", "do", "does", "did", "will", "would",
            "could", "should", "may", "might", "can", "shall", "not", "no", "nor",
            "so", "if", "then", "than", "that", "this", "these", "those", "it",
            "its", "we", "they", "he", "she", "as", "about", "into", "over",
            "after", "before", "between", "under", "again", "further", "here",
            "there", "which", "who", "whom", "whose", "both", "each", "every",
            "all", "any", "few", "more", "most", "other", "some", "such", "only",
            "own", "same", "too", "very", "just", "also", "now", "new", "using",
            "based", "via", "per", "up", "out", "our", "their", "his", "her",
            "one", "two", "three", "well", "yet", "still", "however", "therefore",
            "thus", "although", "because", "since", "while", "where", "when",
            "how", "what"
    );

    private static final Set<String> GENERIC_ACADEMIC_WORDS = Set.of(
            "paper", "study", "research", "method", "approach", "result",
            "analysis", "data", "model", "system", "review", "survey",
            "introduction", "conclusion", "experiment", "evaluation",
            "proposed", "novel", "improved", "efficient", "effective",
            "abstract", "purpose", "background", "related", "work",
            "present", "discuss", "show", "demonstrate", "focus",
            "propose", "develop", "compare", "investigate", "examine"
    );

    private final KeywordRepository keywordRepository;
    private final ResearchPaperRepository researchPaperRepository;

    /**
     * Extract top keywords from title + abstract.
     */
    public List<String> extract(String title, String abstractText) {
        return extract(title, abstractText, DEFAULT_MAX_KEYWORDS);
    }

    /**
     * Extract top N keywords from title + abstract.
     */
    public List<String> extract(String title, String abstractText, int maxKeywords) {
        if (isBlank(title) && isBlank(abstractText)) {
            return List.of();
        }

        String normalizedTitle = normalize(title);
        String normalizedAbstract = normalize(abstractText);
        String combinedText = normalizedTitle + " " + normalizedAbstract;
        String[] words = combinedText.split("\\s+");

        if (words.length < 2) {
            return words.length > 0 && !STOP_WORDS.contains(words[0])
                    ? List.of(words[0])
                    : List.of();
        }

        // Step 1-2: Generate n-gram candidates, filter stop words
        Map<String, CandidateStats> candidates = generateCandidates(words, normalizedTitle);

        // Step 3-4: Score with TF-IDF
        long totalPapers = researchPaperRepository.count();
        List<ScoredKeyword> scored = scoreAndRank(candidates, totalPapers);

        // Step 5: Pick top N
        return scored.stream()
                .limit(Math.max(1, maxKeywords))
                .map(ScoredKeyword::keyword)
                .collect(Collectors.toList());
    }

    // ── Candidate generation ──

    private Map<String, CandidateStats> generateCandidates(String[] words, String normalizedTitle) {
        Map<String, CandidateStats> candidates = new LinkedHashMap<>();

        for (int n = 1; n <= MAX_NGRAM_WORDS; n++) {
            for (int i = 0; i <= words.length - n; i++) {
                String ngram = buildNgram(words, i, n);
                if (isValidCandidate(ngram)) {
                    candidates.computeIfAbsent(ngram, k -> new CandidateStats())
                            .incrementFrequency();
                }
            }
        }

        // Mark which candidates appear in the title
        for (String candidate : candidates.keySet()) {
            if (containsPhrase(normalizedTitle, candidate)) {
                candidates.get(candidate).inTitle = true;
            }
        }

        return candidates;
    }

    private String buildNgram(String[] words, int start, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append(' ');
            sb.append(words[start + i]);
        }
        return sb.toString();
    }

    private boolean isValidCandidate(String ngram) {
        String[] parts = ngram.split(" ");
        if (parts.length == 0) return false;

        String first = parts[0];
        String last = parts[parts.length - 1];

        // Must not start or end with a stop word
        if (STOP_WORDS.contains(first) || STOP_WORDS.contains(last)) {
            return false;
        }

        // Each token must be at least MIN_TOKEN_LENGTH chars
        for (String part : parts) {
            if (part.length() < MIN_TOKEN_LENGTH) return false;
        }

        // Skip generic academic words (for single-word candidates)
        if (parts.length == 1 && GENERIC_ACADEMIC_WORDS.contains(first)) {
            return false;
        }

        return true;
    }

    // ── Scoring ──

    private List<ScoredKeyword> scoreAndRank(Map<String, CandidateStats> candidates, long totalPapers) {
        if (totalPapers <= 0) totalPapers = 1;

        List<ScoredKeyword> scored = new ArrayList<>();

        for (Map.Entry<String, CandidateStats> entry : candidates.entrySet()) {
            String candidate = entry.getKey();
            CandidateStats stats = entry.getValue();

            // TF: frequency in this document
            double tf = stats.frequency;

            // IDF: inverse document frequency from the keyword corpus
            long papersWithTerm = countPapersWithTerm(candidate);
            double idf = Math.log((double) totalPapers / (papersWithTerm + 1)) + 1.0;

            // Title boost
            double titleMultiplier = stats.inTitle ? TITLE_BOOST : 1.0;

            // N-gram length bonus (bigram/trigram are more specific than unigram)
            int wordCount = candidate.split(" ").length;
            double ngramBonus = 1.0 + (wordCount - 1) * 0.3;

            double score = tf * idf * titleMultiplier * ngramBonus;

            scored.add(new ScoredKeyword(candidate, score));
        }

        scored.sort(Comparator.comparingDouble(ScoredKeyword::score).reversed());
        return scored;
    }

    private long countPapersWithTerm(String term) {
        try {
            return keywordRepository.findByNormalizedText(term)
                    .map(kw -> kw.getPaperCount() != null ? (long) kw.getPaperCount() : 0L)
                    .orElse(0L);
        } catch (Exception e) {
            log.debug("Failed to count papers for term '{}': {}", term, e.getMessage());
            return 0L;
        }
    }

    // ── Text normalization ──

    private String normalize(String value) {
        if (value == null) return "";
        return value.toLowerCase()
                .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean containsPhrase(String text, String phrase) {
        if (isBlank(text) || isBlank(phrase)) return false;
        return (" " + text + " ").contains(" " + phrase + " ");
    }

    // ── Internal types ──

    private static class CandidateStats {
        int frequency = 0;
        boolean inTitle = false;

        void incrementFrequency() {
            frequency++;
        }
    }

    private record ScoredKeyword(String keyword, double score) {
    }
}
