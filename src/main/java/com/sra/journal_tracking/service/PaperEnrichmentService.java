package com.sra.journal_tracking.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI-powered paper enrichment — extracts metrics, datasets, and methods
 * from paper abstracts (and optionally full-text).
 * <p>
 * Uses DeepSeek AI via {@link AIClient}. Results cached in-memory (1h TTL)
 * and persisted to RESEARCH_PAPER.enriched_data JSON column.
 */
@Service
public class PaperEnrichmentService {

    private static final Logger log = LoggerFactory.getLogger(PaperEnrichmentService.class);

    private static final long CACHE_TTL_MS = 60 * 60 * 1000; // 1 hour
    private static final int MAX_TOKENS = 1024;
    private static final double TEMPERATURE = 0.1; // Low temp = deterministic extraction
    private static final int MAX_ABSTRACT_CHARS = 3000;
    private static final int MAX_FULLTEXT_CHARS = 5000;

    private final AIClient aiClient;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, CacheEntry<EnrichedData>> cache = new ConcurrentHashMap<>();

    public PaperEnrichmentService(AIClient aiClient, ObjectMapper objectMapper) {
        this.aiClient = aiClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Enrich a paper with metrics, datasets, and methods extracted from its abstract.
     * If full-text is available, it will be used as a supplementary source.
     *
     * @param paperId  the paper UUID
     * @param abstractText the abstract text (required)
     * @param fullText optional full-text (can be null)
     * @return enriched data, or empty result on failure
     */
    public EnrichedData enrich(String paperId, String abstractText, String fullText) {
        // Check cache
        CacheEntry<EnrichedData> cached = cache.get(paperId);
        if (cached != null && !cached.isExpired()) {
            log.info("CACHE HIT: enrichment for paper {}", paperId);
            return cached.data;
        }
        if (cached != null) {
            cache.remove(paperId);
        }

        EnrichedData result = new EnrichedData();

        // Tier 1: Always extract from abstract
        if (abstractText != null && !abstractText.isBlank()) {
            String truncated = abstractText.length() > MAX_ABSTRACT_CHARS
                    ? abstractText.substring(0, MAX_ABSTRACT_CHARS) : abstractText;
            result = extractWithAI(truncated, "abstract");
        }

        // Tier 2: Supplement with full-text if available
        if (fullText != null && !fullText.isBlank()) {
            String truncated = fullText.length() > MAX_FULLTEXT_CHARS
                    ? fullText.substring(0, MAX_FULLTEXT_CHARS) : fullText;
            EnrichedData fullResult = extractWithAI(truncated, "full-text");
            result = mergeResults(result, fullResult);
        }

        // Cache
        cache.put(paperId, new CacheEntry<>(result));
        log.info("CACHE STORE: enrichment for paper {} — {} metrics, {} datasets, {} methods",
                paperId, result.metrics.size(), result.datasets.size(), result.methods.size());

        return result;
    }

    private EnrichedData extractWithAI(String text, String source) {
        String prompt = buildExtractionPrompt(text);
        try {
            String response = aiClient.call(prompt, MAX_TOKENS, TEMPERATURE);
            return parseResponse(response);
        } catch (Exception e) {
            log.warn("AI enrichment failed from {}: {}", source, e.getMessage());
            return new EnrichedData(); // Graceful fallback — return empty
        }
    }

    private String buildExtractionPrompt(String text) {
        // Use simple string replacement instead of String.formatted() to avoid
        // UnknownFormatConversionException when abstracts contain % characters
        // (e.g., "95.2% accuracy", "% of patients", etc.)
        String template = """
                You are a research paper analyzer. Extract metrics, datasets, and methods
                MENTIONED in the text below.

                RULES:
                - Extract dataset NAMES that are explicitly mentioned (e.g., "CIFAR-10", "ImageNet", "MIMIC-III")
                - Extract metric NAMES that are explicitly mentioned (e.g., "Accuracy", "F1-score", "BLEU", "RMSE")
                - Include metric VALUES when available (e.g., "95.2%", "0.89")
                - Extract method/architecture NAMES (e.g., "CNN", "BERT", "Random Forest", "XGBoost")
                - Use "context" field for brief qualifying info (e.g., "for classification", "on test set")
                - If the text only describes things in general terms without naming specific items, return empty arrays.
                - But DO extract named items even if values aren't provided.

                Examples:
                  Text: "We evaluate on CIFAR-10 and ImageNet, achieving 95.2% accuracy"
                  → {"metrics":[{"name":"Accuracy","value":"95.2%"}],"datasets":[{"name":"CIFAR-10"},{"name":"ImageNet"}],"methods":[]}

                  Text: "We propose a Vision Transformer (ViT) architecture and test it on COCO dataset"
                  → {"metrics":[],"datasets":[{"name":"COCO"}],"methods":[{"name":"Vision Transformer","context":"ViT architecture"}]}

                  Text: "Our BERT-based model achieves F1-score of 0.92 on SQuAD"
                  → {"metrics":[{"name":"F1-score","value":"0.92"}],"datasets":[{"name":"SQuAD"}],"methods":[{"name":"BERT"}]}

                  Text: "We outperform existing methods with our approach"
                  → {"metrics":[],"datasets":[],"methods":[]}

                TEXT TO ANALYZE:
                __TEXT__

                Respond with ONLY valid JSON (no markdown, no extra text):
                {"metrics":[{"name":"...","value":"...","context":"..."}],"datasets":[{"name":"...","context":"..."}],"methods":[{"name":"...","context":"..."}]}
                """;
        return template.replace("__TEXT__", text);
    }

    private EnrichedData parseResponse(String response) {
        try {
            // Strip any markdown code fences
            String json = response.trim();
            if (json.startsWith("```")) {
                json = json.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
            }
            // Find the outermost { }
            int start = json.indexOf('{');
            int end = json.lastIndexOf('}');
            if (start >= 0 && end > start) {
                json = json.substring(start, end + 1);
            }

            Map<String, Object> map = objectMapper.readValue(json,
                    new TypeReference<Map<String, Object>>() {});

            EnrichedData result = new EnrichedData();

            @SuppressWarnings("unchecked")
            List<Map<String, String>> metrics = (List<Map<String, String>>) map.getOrDefault("metrics", List.of());
            for (Map<String, String> m : metrics) {
                String name = m.get("name");
                if (name != null && !name.isBlank()) {
                    result.metrics.add(new ExtractedItem(
                            name.trim(),
                            m.getOrDefault("value", ""),
                            m.getOrDefault("context", "")
                    ));
                }
            }

            @SuppressWarnings("unchecked")
            List<Map<String, String>> datasets = (List<Map<String, String>>) map.getOrDefault("datasets", List.of());
            for (Map<String, String> d : datasets) {
                String name = d.get("name");
                if (name != null && !name.isBlank()) {
                    result.datasets.add(new ExtractedItem(
                            name.trim(),
                            "",
                            d.getOrDefault("context", "")
                    ));
                }
            }

            @SuppressWarnings("unchecked")
            List<Map<String, String>> methods = (List<Map<String, String>>) map.getOrDefault("methods", List.of());
            for (Map<String, String> m : methods) {
                String name = m.get("name");
                if (name != null && !name.isBlank()) {
                    result.methods.add(new ExtractedItem(
                            name.trim(),
                            "",
                            m.getOrDefault("context", "")
                    ));
                }
            }

            return result;
        } catch (Exception e) {
            log.warn("Failed to parse AI enrichment response: {}", e.getMessage());
            return new EnrichedData();
        }
    }

    private EnrichedData mergeResults(EnrichedData abstractResult, EnrichedData fullTextResult) {
        // Merge: add items from full-text that don't already exist in abstract result
        Set<String> existingDatasets = new HashSet<>();
        Set<String> existingMetrics = new HashSet<>();
        Set<String> existingMethods = new HashSet<>();

        for (ExtractedItem d : abstractResult.datasets) existingDatasets.add(d.name.toLowerCase());
        for (ExtractedItem m : abstractResult.metrics) existingMetrics.add(m.name.toLowerCase());
        for (ExtractedItem m : abstractResult.methods) existingMethods.add(m.name.toLowerCase());

        for (ExtractedItem d : fullTextResult.datasets) {
            if (!existingDatasets.contains(d.name.toLowerCase())) {
                abstractResult.datasets.add(d);
            }
        }
        for (ExtractedItem m : fullTextResult.metrics) {
            if (!existingMetrics.contains(m.name.toLowerCase())) {
                abstractResult.metrics.add(m);
            }
        }
        for (ExtractedItem m : fullTextResult.methods) {
            if (!existingMethods.contains(m.name.toLowerCase())) {
                abstractResult.methods.add(m);
            }
        }

        return abstractResult;
    }

    // ── Inner types ──

    public static class EnrichedData {
        public List<ExtractedItem> metrics = new ArrayList<>();
        public List<ExtractedItem> datasets = new ArrayList<>();
        public List<ExtractedItem> methods = new ArrayList<>();

        public boolean isEmpty() {
            return metrics.isEmpty() && datasets.isEmpty() && methods.isEmpty();
        }
    }

    public static class ExtractedItem {
        public String name;
        public String value;    // only for metrics
        public String context;

        public ExtractedItem() {}

        public ExtractedItem(String name, String value, String context) {
            this.name = name;
            this.value = value;
            this.context = context;
        }
    }

    private static class CacheEntry<T> {
        final T data;
        final long expiryTime;
        CacheEntry(T data) {
            this.data = data;
            this.expiryTime = System.currentTimeMillis() + CACHE_TTL_MS;
        }
        boolean isExpired() { return System.currentTimeMillis() > expiryTime; }
    }
}
