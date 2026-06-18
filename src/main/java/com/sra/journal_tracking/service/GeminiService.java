package com.sra.journal_tracking.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sra.journal_tracking.dto.GeminiRequestDTO;
import com.sra.journal_tracking.dto.GeminiResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;

/**
 * Service to call Google Gemini API for keyword expansion.
 * Generates semantically related keywords for a given research topic.
 */
@Service
public class GeminiService {

    private static final Logger log = LoggerFactory.getLogger(GeminiService.class);

    private static final String DEFAULT_GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";

    private static final String KEYWORD_EXPANSION_PROMPT =
            "You are a research keyword expander. Given a research topic keyword, "
            + "generate 15 semantically related keywords, subtopics, or related terms "
            + "that a researcher might also search for. "
            + "Return ONLY a JSON array of strings, no explanation, no markdown formatting. "
            + "Example input: \"machine learning\" → "
            + "[\"deep learning\",\"neural networks\",\"supervised learning\",\"NLP\",\"computer vision\","
            + "\"reinforcement learning\",\"data mining\",\"artificial intelligence\",\"MLOps\","
            + "\"feature engineering\",\"gradient descent\",\"transfer learning\",\"GANs\","
            + "\"random forest\",\"support vector machines\"]\n"
            + "Keyword: \"%s\"";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.gemini.api-key:}")
    private String apiKey;

    @Value("${app.gemini.model:gemini-2.0-flash}")
    private String model;

    @Value("${app.gemini.api-url:}")
    private String apiUrl;

    public GeminiService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Builds the Gemini API URL from config (or default template).
     */
    private String buildUrl() {
        if (apiUrl != null && !apiUrl.isBlank()) {
            return apiUrl + "?key=" + apiKey;
        }
        return String.format(DEFAULT_GEMINI_URL, model) + "?key=" + apiKey;
    }

    /**
     * Expands a keyword into a list of semantically related keywords using Gemini API.
     * Results are cached via Caffeine to avoid repeated API calls.
     *
     * @param keyword the user's search keyword
     * @return list of 15 related keywords (or just the original keyword on failure)
     */
    @Cacheable(value = "geminiKeywords", key = "#keyword.toLowerCase().trim()")
    public List<String> expandKeyword(String keyword) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Gemini API key is not configured (app.gemini.api-key is empty). "
                    + "Set GEMINI_API_KEY in .env or gemini.api.key in .env");
            return Collections.singletonList(keyword.trim());
        }

        String url = buildUrl();
        String prompt = String.format(KEYWORD_EXPANSION_PROMPT, keyword.trim());

        log.info("Calling Gemini API (model={}) to expand keyword: '{}'", model, keyword);

        try {
            GeminiRequestDTO request = GeminiRequestDTO.fromPrompt(prompt);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<GeminiRequestDTO> entity = new HttpEntity<>(request, headers);

            ResponseEntity<GeminiResponseDTO> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, GeminiResponseDTO.class);

            if (response.getBody() == null) {
                log.warn("Gemini returned empty body for keyword '{}' (model={})", keyword, model);
                return fallbackKeywords(keyword);
            }

            String rawText = response.getBody().extractText();
            if (rawText == null || rawText.isBlank()) {
                log.warn("Gemini returned empty text for keyword '{}' (model={})", keyword, model);
                return fallbackKeywords(keyword);
            }

            List<String> keywords = parseKeywordArray(rawText);
            if (keywords.isEmpty()) {
                log.warn("Failed to parse keywords from Gemini response for '{}'. Raw: {}", keyword, rawText);
                return fallbackKeywords(keyword);
            }

            // Deduplicate and normalize
            List<String> normalized = keywords.stream()
                    .map(String::trim)
                    .filter(k -> !k.isEmpty())
                    .distinct()
                    .toList();

            log.info("Gemini expanded '{}' → {} keywords: {}", keyword, normalized.size(), normalized);
            return normalized;

        } catch (HttpClientErrorException e) {
            // 4xx errors (quota, auth, etc.) — log response body for debugging
            log.error("Gemini API HTTP {} for keyword '{}' (model={}): {}",
                    e.getStatusCode().value(), keyword, model, e.getResponseBodyAsString());
            return fallbackKeywords(keyword);
        } catch (RestClientException e) {
            log.error("Gemini API call failed for keyword '{}' (model={}): {}", keyword, model, e.getMessage());
            return fallbackKeywords(keyword);
        } catch (Exception e) {
            log.error("Unexpected error calling Gemini for keyword '{}' (model={}): {}", keyword, model, e.getMessage(), e);
            return fallbackKeywords(keyword);
        }
    }

    /**
     * Parses a JSON array string like ["a","b","c"] into a List of strings.
     * Handles common Gemini formatting quirks (markdown code blocks, extra whitespace).
     */
    @SuppressWarnings("unchecked")
    private List<String> parseKeywordArray(String rawText) {
        try {
            // Strip markdown code fences if present
            String cleaned = rawText.trim();
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.replaceAll("```(?:json)?\\s*", "").trim();
            }

            return objectMapper.readValue(cleaned, List.class);
        } catch (JsonProcessingException e) {
            log.debug("Failed to parse Gemini response as JSON array: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Fallback: return just the original keyword so the graph still works
     * even when Gemini is unavailable.
     */
    private List<String> fallbackKeywords(String keyword) {
        return Collections.singletonList(keyword.trim());
    }
}
