package com.sra.journal_tracking.service;

import com.sra.journal_tracking.dto.gap.FuzzyMatchSuggestion;
import com.sra.journal_tracking.dto.gap.FuzzyMatchSuggestion.FuzzyCandidate;
import com.sra.journal_tracking.dto.gap.KeywordLandscapeItem;
import com.sra.journal_tracking.dto.gap.KeywordMatchResult;
import com.sra.journal_tracking.dto.gap.KeywordMatchResult.KeywordExactMatch;
import com.sra.journal_tracking.dto.gap.KeywordMatchResult.MatchLevel;
import com.sra.journal_tracking.dto.gap.KeywordPreview;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Multi-layer keyword matching service for the Research Gap Explorer.
 * <p>
 * When a user describes a research idea, this service checks whether the
 * concepts map to keywords that exist in Neo4j. If not, it progressively
 * falls back through 4 layers:
 * <ol>
 *   <li><b>Exact + Synonym Match</b> — expand terms via
 *       {@link KeywordExpansionService}, check existence in Neo4j.</li>
 *   <li><b>Fuzzy Match</b> — Levenshtein + prefix-overlap similarity
 *       for terms with no exact match.</li>
 *   <li><b>Partial Match</b> — work with what matched, note what didn't.</li>
 *   <li><b>Guided Discovery</b> — show the available knowledge landscape.</li>
 * </ol>
 */
@Service
public class KeywordMatchingService {

    private static final Logger log = LoggerFactory.getLogger(KeywordMatchingService.class);

    /** Minimum similarity to consider a fuzzy match (0.0–1.0). */
    private static final double MIN_FUZZY_SIMILARITY = 0.65;

    /** Maximum number of fuzzy candidates per unmatched term. */
    private static final int MAX_FUZZY_CANDIDATES = 3;

    /** Maximum keywords per landscape item. */
    private static final int MAX_LANDSCAPE_KEYWORDS = 6;

    /** Minimum papers a keyword needs to appear in the landscape. */
    private static final int MIN_LANDSCAPE_PAPER_COUNT = 3;

    /** Stopwords to skip when building n-grams from idea text. */
    private static final Set<String> STOP_WORDS = Set.of(
            "and", "or", "the", "of", "in", "on", "at", "to", "for", "with",
            "a", "an", "by", "from", "is", "are", "was", "were", "be", "been",
            "i", "we", "you", "they", "my", "our", "your", "their",
            "want", "like", "explore", "find", "search", "looking",
            "about", "how", "what", "can", "could", "would", "should",
            "this", "that", "these", "those", "it", "its",
            "using", "based", "via", "has", "have", "had", "not", "no",
            "also", "but", "however", "although", "while", "where", "when",
            "which", "who", "whom", "whose", "will", "may", "might", "must",
            "do", "does", "did", "done", "doing", "get", "got", "use", "used"
    );

    private final KeywordExpansionService keywordExpansionService;
    private final GraphExplorerService graphExplorerService;
    private final GraphService graphService;

    public KeywordMatchingService(KeywordExpansionService keywordExpansionService,
                                   GraphExplorerService graphExplorerService,
                                   GraphService graphService) {
        this.keywordExpansionService = keywordExpansionService;
        this.graphExplorerService = graphExplorerService;
        this.graphService = graphService;
    }

    // ═══════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════

    /**
     * Match a user's idea text against available Neo4j keywords.
     *
     * @param ideaText the free-text research idea (e.g. "blockchain trong healthcare")
     * @return structured match result with exact matches, fuzzy suggestions,
     *         unmatched terms, and the determined match level
     */
    public KeywordMatchResult matchIdea(String ideaText) {
        KeywordMatchResult result = new KeywordMatchResult();

        // ── Step 1: Extract single-token expanded terms from the idea ──
        List<String> expandedTerms = keywordExpansionService.expand(ideaText);

        // ── Step 1b: ALSO extract n-grams (bigrams + trigrams) to match multi-word phrases ──
        List<String> ngrams = extractNGrams(ideaText);

        // Merge + deduplicate
        List<String> allTerms = new ArrayList<>(expandedTerms);
        allTerms.addAll(ngrams);
        allTerms = allTerms.stream().distinct().collect(Collectors.toList());

        log.debug("Terms for '{}': {} single + {} ngrams = {} total",
                ideaText.length() > 40 ? ideaText.substring(0, 40) + "..." : ideaText,
                expandedTerms.size(), ngrams.size(), allTerms.size());

        // ── Layer 1: Exact + Synonym match ──
        List<KeywordExactMatch> exactMatches = new ArrayList<>();
        List<String> unmatchedTerms = new ArrayList<>();
        List<Map<String, Object>> allKeywords = graphExplorerService.listAllKeywords();

        // Build a fast lookup: normalizedText → {text, paperCount}
        Map<String, Map<String, Object>> keywordLookup = new HashMap<>();
        for (Map<String, Object> kw : allKeywords) {
            String norm = (String) kw.get("normalizedText");
            if (norm != null && !norm.isBlank()) {
                keywordLookup.put(norm.toLowerCase(), kw);
            }
        }

        for (String term : allTerms) {
            String normalized = keywordExpansionService.normalize(term);
            if (normalized.isBlank()) continue;

            Map<String, Object> match = keywordLookup.get(normalized);
            if (match != null) {
                int paperCount = match.get("paperCount") instanceof Number
                        ? ((Number) match.get("paperCount")).intValue() : 0;
                if (paperCount == 0) {
                    paperCount = (int) graphService.countPapersByKeyword(normalized);
                }
                exactMatches.add(new KeywordExactMatch(
                        (String) match.get("text"), normalized, paperCount));
            } else {
                unmatchedTerms.add(term);
            }
        }

        // ── Prioritize longer matches (trigram > bigram > single token) ──
        // If "wearable devices" matched, demote individual "wearable" and "devices"
        exactMatches = prioritizeLongerMatches(exactMatches);

        // ── Layer 2: Fuzzy match for unmatched terms ──
        List<FuzzyMatchSuggestion> fuzzySuggestions = new ArrayList<>();
        if (!unmatchedTerms.isEmpty()) {
            fuzzySuggestions = tryFuzzyMatch(unmatchedTerms, keywordLookup);
        }

        // ── Determine match level ──
        int exactCount = exactMatches.size();
        int fuzzyCandidateCount = (int) fuzzySuggestions.stream()
                .filter(fs -> fs.getCandidates() != null && !fs.getCandidates().isEmpty())
                .count();

        if (exactCount >= 2) {
            result.setMatchLevel(MatchLevel.FULL);
        } else if (exactCount == 1) {
            result.setMatchLevel(MatchLevel.PARTIAL);
        } else if (fuzzyCandidateCount > 0) {
            result.setMatchLevel(MatchLevel.FUZZY_ONLY);
        } else {
            result.setMatchLevel(MatchLevel.NONE);
        }

        // ── Build guidance message ──
        result.setGuidanceMessage(buildGuidance(result.getMatchLevel(), exactMatches, unmatchedTerms));

        // ── Layer 4: Always prepare landscape (used when NONE, helpful for others) ──
        if (result.getMatchLevel() == MatchLevel.NONE || result.getMatchLevel() == MatchLevel.FUZZY_ONLY) {
            result.setAvailableLandscape(buildLandscape(allKeywords));
        }

        result.setExactMatches(exactMatches);
        result.setFuzzySuggestions(fuzzySuggestions);
        result.setUnmatchedTerms(unmatchedTerms);

        log.info("Keyword match for idea: level={}, exact={}, fuzzy={}, unmatched={}",
                result.getMatchLevel(), exactCount, fuzzyCandidateCount, unmatchedTerms.size());
        return result;
    }

    // ═══════════════════════════════════════════════════
    // N-GRAM EXTRACTION
    // ═══════════════════════════════════════════════════

    /**
     * Extract bigrams and trigrams from idea text after removing stopwords.
     * <p>
     * Example: "AI in wearable devices for healthcare"
     * → bigrams: ["wearable devices", "devices healthcare"]
     * → trigrams: ["wearable devices healthcare"]
     * <p>
     * These capture multi-word concepts that single-token expansion misses.
     */
    private List<String> extractNGrams(String ideaText) {
        // Tokenize: normalize, split, remove stopwords + short tokens
        String[] words = Arrays.stream(keywordExpansionService.normalize(ideaText).split(" "))
                .filter(w -> w.length() >= 2)
                .filter(w -> !STOP_WORDS.contains(w))
                .toArray(String[]::new);

        if (words.length < 2) return List.of();

        List<String> ngrams = new ArrayList<>();

        // Bigrams
        for (int i = 0; i < words.length - 1; i++) {
            ngrams.add(words[i] + " " + words[i + 1]);
        }

        // Trigrams (only if enough words)
        if (words.length >= 3) {
            for (int i = 0; i < words.length - 2; i++) {
                ngrams.add(words[i] + " " + words[i + 1] + " " + words[i + 2]);
            }
        }

        return ngrams;
    }

    /**
     * Prioritize longer keyword matches over shorter ones.
     * <p>
     * If "wearable devices" (bigram) matched AND "wearable" + "devices" also matched,
     * keep the bigram and remove the individual words.
     * Longer phrases carry more semantic meaning.
     */
    private List<KeywordExactMatch> prioritizeLongerMatches(List<KeywordExactMatch> matches) {
        if (matches.size() <= 1) return matches;

        // Sort by word count descending: trigram > bigram > single token
        matches.sort((a, b) -> Integer.compare(
                b.getText().split("\\s+").length,
                a.getText().split("\\s+").length));

        Set<String> coveredWords = new HashSet<>();
        List<KeywordExactMatch> prioritized = new ArrayList<>();

        for (KeywordExactMatch match : matches) {
            String[] words = match.getNormalizedText().split("\\s+");
            boolean allWordsCovered = words.length == 1
                    && Arrays.stream(words).allMatch(coveredWords::contains);

            if (!allWordsCovered) {
                prioritized.add(match);
                Arrays.stream(words).forEach(coveredWords::add);
            }
        }

        return prioritized;
    }

    // ═══════════════════════════════════════════════════
    // LAYER 2: FUZZY MATCHING
    // ═══════════════════════════════════════════════════

    /**
     * For each unmatched term, find the top-3 closest Neo4j keywords
     * using combined Levenshtein + prefix-overlap similarity.
     */
    private List<FuzzyMatchSuggestion> tryFuzzyMatch(
            List<String> unmatchedTerms,
            Map<String, Map<String, Object>> keywordLookup) {

        if (keywordLookup.isEmpty()) return List.of();

        List<FuzzyMatchSuggestion> results = new ArrayList<>();

        for (String term : unmatchedTerms) {
            String normalized = keywordExpansionService.normalize(term);
            if (normalized.isBlank()) continue;

            // Score every keyword against this term
            List<FuzzyCandidate> scored = new ArrayList<>();
            for (Map.Entry<String, Map<String, Object>> entry : keywordLookup.entrySet()) {
                String kwNorm = entry.getKey();
                double sim = computeSimilarity(normalized, kwNorm);
                if (sim >= MIN_FUZZY_SIMILARITY) {
                    Map<String, Object> kw = entry.getValue();
                    int paperCount = kw.get("paperCount") instanceof Number
                            ? ((Number) kw.get("paperCount")).intValue() : 0;
                    scored.add(new FuzzyCandidate(
                            (String) kw.get("text"), kwNorm, sim, paperCount));
                }
            }

            // Sort by similarity desc, take top 3
            scored.sort((a, b) -> Double.compare(b.getSimilarity(), a.getSimilarity()));
            List<FuzzyCandidate> topCandidates = scored.stream()
                    .limit(MAX_FUZZY_CANDIDATES)
                    .collect(Collectors.toList());

            results.add(new FuzzyMatchSuggestion(term, topCandidates));
        }

        return results;
    }

    /**
     * Compute a combined similarity score between two strings.
     * <p>
     * Formula: 40% normalized Levenshtein + 30% prefix match + 30% containment bonus.
     * No external dependencies needed.
     */
    private double computeSimilarity(String s1, String s2) {
        // 1. Normalized Levenshtein (0.0–1.0, higher = more similar)
        double levenshtein = 1.0 - (double) levenshteinDistance(s1, s2)
                / Math.max(s1.length(), s2.length());

        // 2. Prefix match — how many chars match from the start
        int prefixLen = commonPrefixLength(s1, s2);
        double prefixScore = (double) prefixLen / Math.max(s1.length(), s2.length());

        // 3. Containment bonus — one string contains the other
        double containment = (s1.contains(s2) || s2.contains(s1)) ? 0.3 : 0.0;

        return (levenshtein * 0.4) + (prefixScore * 0.3) + containment;
    }

    /**
     * Compute Levenshtein (edit) distance between two strings.
     * Uses O(n) space (single-row DP).
     */
    private int levenshteinDistance(String a, String b) {
        if (a.isEmpty()) return b.length();
        if (b.isEmpty()) return a.length();

        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];

        for (int j = 0; j <= b.length(); j++) prev[j] = j;

        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(
                        prev[j] + 1,      // deletion
                        curr[j - 1] + 1),  // insertion
                        prev[j - 1] + cost // substitution
                );
            }
            int[] temp = prev;
            prev = curr;
            curr = temp;
        }
        return prev[b.length()];
    }

    private int commonPrefixLength(String a, String b) {
        int max = Math.min(a.length(), b.length());
        int i = 0;
        while (i < max && a.charAt(i) == b.charAt(i)) i++;
        return i;
    }

    // ═══════════════════════════════════════════════════
    // LAYER 4: GUIDED DISCOVERY LANDSCAPE
    // ═══════════════════════════════════════════════════

    /**
     * Build the available knowledge landscape from all keywords.
     * Groups by field → topic (inferred from keyword co-occurrence clusters).
     */
    private List<KeywordLandscapeItem> buildLandscape(List<Map<String, Object>> allKeywords) {
        // For now, group by paper count tiers as a simple landscape.
        // Future: integrate with GraphExplorerService.getHierarchyGraph() for true field/topic grouping.
        List<Map<String, Object>> sorted = allKeywords.stream()
                .filter(kw -> {
                    Object pc = kw.get("paperCount");
                    return pc instanceof Number && ((Number) pc).intValue() >= MIN_LANDSCAPE_PAPER_COUNT;
                })
                .sorted((a, b) -> {
                    int ca = a.get("paperCount") instanceof Number ? ((Number) a.get("paperCount")).intValue() : 0;
                    int cb = b.get("paperCount") instanceof Number ? ((Number) b.get("paperCount")).intValue() : 0;
                    return Integer.compare(cb, ca);
                })
                .collect(Collectors.toList());

        List<KeywordLandscapeItem> landscape = new ArrayList<>();

        if (!sorted.isEmpty()) {
            // Build one "All Topics" landscape item
            List<KeywordPreview> previews = sorted.stream()
                    .limit(MAX_LANDSCAPE_KEYWORDS)
                    .map(kw -> new KeywordPreview(
                            (String) kw.get("text"),
                            (String) kw.get("normalizedText"),
                            kw.get("paperCount") instanceof Number
                                    ? ((Number) kw.get("paperCount")).intValue() : 0,
                            false))
                    .collect(Collectors.toList());

            landscape.add(new KeywordLandscapeItem(
                    "Computer Science & AI", "Top Keywords", previews));
        }

        // Try to get field/topic info from hierarchy graph
        try {
            List<Integer> years = List.of(2024, 2025, 2026);
            var hierarchyResult = graphExplorerService.getHierarchyGraph(years);
            if (hierarchyResult != null && hierarchyResult.getNodes() != null) {
                // Extract field → keyword groupings from the graph
                Map<String, List<KeywordPreview>> fieldGroups = new LinkedHashMap<>();
                hierarchyResult.getNodes().stream()
                        .filter(n -> "FIELD".equals(n.getGroup()))
                        .forEach(fieldNode -> {
                            String fieldName = fieldNode.getLabel();
                            List<KeywordPreview> kws = hierarchyResult.getLinks().stream()
                                    .filter(l -> l.getTarget().equals(fieldNode.getId()))
                                    .map(l -> hierarchyResult.getNodes().stream()
                                            .filter(n -> n.getId().equals(l.getSource()))
                                            .findFirst().orElse(null))
                                    .filter(Objects::nonNull)
                                    .filter(n -> "KEYWORD".equals(n.getGroup()))
                                    .map(n -> new KeywordPreview(n.getLabel(), n.getLabel().toLowerCase(),
                                            n.getSize(), false))
                                    .limit(MAX_LANDSCAPE_KEYWORDS)
                                    .collect(Collectors.toList());
                            if (!kws.isEmpty()) {
                                fieldGroups.put(fieldName, kws);
                            }
                        });

                // Replace simple landscape with field-grouped one
                if (!fieldGroups.isEmpty()) {
                    landscape.clear();
                    fieldGroups.forEach((fieldName, kws) ->
                            landscape.add(new KeywordLandscapeItem(fieldName, null, kws)));
                }
            }
        } catch (Exception e) {
            log.debug("Could not enrich landscape from hierarchy graph: {}", e.getMessage());
        }

        return landscape;
    }

    // ═══════════════════════════════════════════════════
    // GUIDANCE MESSAGE
    // ═══════════════════════════════════════════════════

    private String buildGuidance(MatchLevel level, List<KeywordExactMatch> exactMatches,
                                  List<String> unmatchedTerms) {
        return switch (level) {
            case FULL -> "Found matching keywords in the system. Here are suggested research gaps.";
            case PARTIAL -> {
                String foundTerms = exactMatches.stream()
                        .map(KeywordExactMatch::getText)
                        .collect(Collectors.joining(", "));
                String missingTerms = String.join(", ", unmatchedTerms);
                yield "Partially matched your idea. Found: " + foundTerms
                        + ". Not found in our system: " + missingTerms
                        + ". Showing results for available keywords.";
            }
            case FUZZY_ONLY -> "Could not find exact matches for your terms. Here are the closest keywords in our system — please confirm which ones you meant.";
            case NONE -> "No matching concepts found in our system. We currently index Computer Science & AI papers (2024-2026). Here are topics you can explore.";
        };
    }
}
