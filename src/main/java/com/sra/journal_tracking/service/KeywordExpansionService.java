package com.sra.journal_tracking.service;

import com.sra.journal_tracking.repository.jpa.PaperKeywordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KeywordExpansionService {

    private static final int DEFAULT_MAX_TERMS = 16;
    private static final double MIN_RELATED_KEYWORD_SCORE = 0.55d;

    private static final Set<String> STOP_WORDS = Set.of(
            "and", "or", "the", "of", "in", "on", "for", "to", "a", "an", "with", "by", "from"
    );

    private static final Map<String, List<String>> SYNONYMS = Map.ofEntries(
            Map.entry("car", List.of("automobile", "vehicle", "automotive", "engine", "tire", "wheel", "brake", "electric vehicle", "transportation")),
            Map.entry("cars", List.of("car", "automobile", "vehicle", "automotive", "engine", "tire", "wheel")),
            Map.entry("automobile", List.of("car", "vehicle", "automotive", "engine", "tire", "wheel")),
            Map.entry("vehicle", List.of("car", "automobile", "automotive", "engine", "tire", "wheel", "transportation")),
            Map.entry("machine learning", List.of("artificial intelligence", "deep learning", "supervised learning", "unsupervised learning", "reinforcement learning", "neural network")),
            Map.entry("statistics", List.of("statistical analysis", "descriptive statistics", "regression analysis", "data analysis", "survey data")),
            Map.entry("statistic", List.of("statistics", "statistical analysis", "data analysis")),
            Map.entry("data", List.of("dataset", "data analysis", "data collection", "statistics", "analytics")),
            Map.entry("phenomenon", List.of("phenomena", "effect", "behavior", "pattern")),
            Map.entry("phenomena", List.of("phenomenon", "effects", "behaviors", "patterns")),
            Map.entry("ai", List.of("artificial intelligence", "machine learning", "deep learning", "large language model")),
            Map.entry("artificial intelligence", List.of("ai", "machine learning", "deep learning", "large language model"))
    );

    private final PaperKeywordRepository paperKeywordRepository;

    public List<String> expand(String query) {
        return expand(query, DEFAULT_MAX_TERMS);
    }

    public List<String> expand(String query, int maxTerms) {
        LinkedHashMap<String, String> termsByNormalized = new LinkedHashMap<>();

        String normalizedQuery = normalize(query);
        if (!normalizedQuery.isBlank()) {
            addTerm(termsByNormalized, normalizedQuery);
        }

        List<String> tokens = extractTokens(query);
        if (tokens.size() <= 1) {
            for (String token : tokens) {
                addTerm(termsByNormalized, token);
                tokenVariants(token).forEach(variant -> addTerm(termsByNormalized, variant));
                SYNONYMS.getOrDefault(token, List.of()).forEach(term -> addTerm(termsByNormalized, term));
            }
        }

        SYNONYMS.getOrDefault(normalizedQuery, List.of()).forEach(term -> addTerm(termsByNormalized, term));

        addLearnedRelatedTerms(termsByNormalized, maxTerms);

        return termsByNormalized.values().stream()
                .limit(Math.max(1, maxTerms))
                .collect(Collectors.toList());
    }

    public List<String> extractTokens(String query) {
        String normalized = normalize(query);
        if (normalized.isBlank()) {
            return List.of();
        }
        return Arrays.stream(normalized.split(" "))
                .filter(token -> token.length() >= 2)
                .filter(token -> !STOP_WORDS.contains(token))
                .distinct()
                .collect(Collectors.toList());
    }

    public String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase()
                .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private void addLearnedRelatedTerms(LinkedHashMap<String, String> termsByNormalized, int maxTerms) {
        List<String> normalizedTerms = new ArrayList<>(termsByNormalized.keySet());
        if (normalizedTerms.isEmpty() || normalizedTerms.size() >= maxTerms) {
            return;
        }

        try {
            paperKeywordRepository.findRelatedKeywordTexts(
                            normalizedTerms,
                            MIN_RELATED_KEYWORD_SCORE,
                            PageRequest.of(0, Math.max(4, maxTerms - normalizedTerms.size())))
                    .forEach(term -> addTerm(termsByNormalized, term));
        } catch (Exception e) {
            log.debug("Keyword co-occurrence expansion skipped: {}", e.getMessage());
        }
    }

    private void addTerm(LinkedHashMap<String, String> termsByNormalized, String term) {
        String normalized = normalize(term);
        if (!normalized.isBlank() && !STOP_WORDS.contains(normalized)) {
            termsByNormalized.putIfAbsent(normalized, term.trim());
        }
    }

    private List<String> tokenVariants(String token) {
        List<String> variants = new ArrayList<>();
        if ("phenomenon".equals(token)) {
            variants.add("phenomena");
        } else if ("phenomena".equals(token)) {
            variants.add("phenomenon");
        }
        if (token.endsWith("y") && token.length() > 3) {
            variants.add(token.substring(0, token.length() - 1) + "ies");
        } else if (token.endsWith("ies") && token.length() > 4) {
            variants.add(token.substring(0, token.length() - 3) + "y");
        } else if (token.endsWith("s") && token.length() > 3) {
            variants.add(token.substring(0, token.length() - 1));
        } else if (token.length() > 3) {
            variants.add(token + "s");
        }
        return variants.stream().distinct().collect(Collectors.toList());
    }
}
