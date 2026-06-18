package com.sra.journal_tracking.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sra.journal_tracking.dto.GroqRequestDTO;
import com.sra.journal_tracking.dto.GroqResponseDTO;
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
 * Service to call Groq API (OpenAI-compatible) for keyword expansion.
 * Groq offers free tier with models like llama-3.1-8b-instant, mixtral-8x7b, etc.
 *
 * Sign up at: https://console.groq.com
 */
@Service
public class GroqService {

    private static final Logger log = LoggerFactory.getLogger(GroqService.class);

    private static final String GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions";

    private static final String SYSTEM_PROMPT =
            "You are a research keyword expander. Given a research topic keyword, "
            + "generate 15 semantically related keywords, subtopics, or related terms "
            + "that a researcher might also search for.";

    private static final String USER_PROMPT_TEMPLATE =
            "Generate 15 related keywords for: \"%s\". "
            + "Return ONLY a JSON array of strings, no explanation, no markdown. "
            + "Example: [\"keyword1\",\"keyword2\",\"keyword3\",...]";

    private static final String HOT_KEYWORDS_SYSTEM_PROMPT =
            "You are a research trend analyst. Your task is to provide the current "
            + "most trending, hot, and popular research keywords across various academic fields. "
            + "These should represent what researchers around the world are actively searching for, "
            + "discussing, and publishing about RIGHT NOW. "
            + "Focus on emerging topics, breakthroughs, and high-interest areas in science, "
            + "technology, medicine, social sciences, and other academic disciplines. "
            + "Do NOT include overly generic terms like \"science\" or \"research\". "
            + "Each keyword should be specific enough to be useful for a literature search. "
            + "Return ONLY a JSON array of strings, no explanation, no markdown formatting. "
            + "Example: [\"large language models\",\"CRISPR gene editing\",\"climate adaptation\","
            + "\"quantum computing\",\"mRNA vaccines\",\"explainable AI\","
            + "\"mental health interventions\",\"sustainable energy\",\"blockchain governance\",...]";

    private static final String HOT_KEYWORDS_USER_PROMPT =
            "List %d of the most trending and hot research keywords right now. "
            + "Return ONLY a JSON array of strings, no explanation, no markdown.";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.groq.api-key:}")
    private String apiKey;

    @Value("${app.groq.model:llama-3.1-8b-instant}")
    private String model;

    public GroqService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Expands a keyword into a list of semantically related keywords using Groq API.
     * Results are cached via Caffeine to avoid repeated API calls.
     *
     * @param keyword the user's search keyword
     * @return list of 15 related keywords (or just the original keyword on failure)
     */
    @Cacheable(value = "groqKeywords", key = "#keyword.toLowerCase().trim()")
    public List<String> expandKeyword(String keyword) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Groq API key is not configured. Set GROQ_API_KEY in .env");
            return Collections.singletonList(keyword.trim());
        }

        log.info("Calling Groq API (model={}) to expand keyword: '{}'", model, keyword);

        try {
            GroqRequestDTO request = GroqRequestDTO.builder()
                    .model(model)
                    .messages(List.of(
                            GroqRequestDTO.Message.system(SYSTEM_PROMPT),
                            GroqRequestDTO.Message.user(String.format(USER_PROMPT_TEMPLATE, keyword.trim()))
                    ))
                    .temperature(0.3)
                    .maxTokens(300)
                    .build();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            HttpEntity<GroqRequestDTO> entity = new HttpEntity<>(request, headers);

            ResponseEntity<GroqResponseDTO> response = restTemplate.exchange(
                    GROQ_API_URL, HttpMethod.POST, entity, GroqResponseDTO.class);

            if (response.getBody() == null) {
                log.warn("Groq returned empty body for keyword '{}'", keyword);
                return fallbackKeywords(keyword);
            }

            String rawContent = response.getBody().extractContent();
            if (rawContent == null || rawContent.isBlank()) {
                log.warn("Groq returned empty content for keyword '{}'", keyword);
                return fallbackKeywords(keyword);
            }

            List<String> keywords = parseKeywordArray(rawContent);
            if (keywords.isEmpty()) {
                log.warn("Failed to parse keywords from Groq response for '{}'. Raw: {}", keyword, rawContent);
                return fallbackKeywords(keyword);
            }

            List<String> normalized = keywords.stream()
                    .map(String::trim)
                    .filter(k -> !k.isEmpty())
                    .distinct()
                    .toList();

            log.info("Groq expanded '{}' → {} keywords: {}", keyword, normalized.size(), normalized);
            return normalized;

        } catch (HttpClientErrorException e) {
            log.error("Groq API HTTP {} for keyword '{}' (model={}): {}",
                    e.getStatusCode().value(), keyword, model, e.getResponseBodyAsString());
            return fallbackKeywords(keyword);
        } catch (RestClientException e) {
            log.error("Groq API call failed for keyword '{}' (model={}): {}", keyword, model, e.getMessage());
            return fallbackKeywords(keyword);
        } catch (Exception e) {
            log.error("Unexpected error calling Groq for keyword '{}' (model={}): {}", keyword, model, e.getMessage(), e);
            return fallbackKeywords(keyword);
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> parseKeywordArray(String rawText) {
        try {
            String cleaned = rawText.trim();
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.replaceAll("```(?:json)?\\s*", "").trim();
            }
            return objectMapper.readValue(cleaned, List.class);
        } catch (JsonProcessingException e) {
            log.debug("Failed to parse Groq response as JSON array: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Fetches current hot/trending research keywords from Groq AI.
     * The AI is prompted to return the most trending research topics across academic fields.
     * Results are cached via Caffeine to avoid repeated API calls.
     *
     * @param count number of hot keywords to return
     * @return list of hot/trending research keywords (or empty list on failure)
     */
    @Cacheable(value = "hotKeywords", key = "'groq-hot-' + #count")
    public List<String> getHotKeywords(int count) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Groq API key is not configured. Set GROQ_API_KEY in .env");
            return Collections.emptyList();
        }

        log.info("Calling Groq API (model={}) to get {} hot keywords", model, count);

        try {
            GroqRequestDTO request = GroqRequestDTO.builder()
                    .model(model)
                    .messages(List.of(
                            GroqRequestDTO.Message.system(HOT_KEYWORDS_SYSTEM_PROMPT),
                            GroqRequestDTO.Message.user(String.format(HOT_KEYWORDS_USER_PROMPT, count))
                    ))
                    .temperature(0.7)
                    .maxTokens(500)
                    .build();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            HttpEntity<GroqRequestDTO> entity = new HttpEntity<>(request, headers);

            ResponseEntity<GroqResponseDTO> response = restTemplate.exchange(
                    GROQ_API_URL, HttpMethod.POST, entity, GroqResponseDTO.class);

            if (response.getBody() == null) {
                log.warn("Groq returned empty body for hot keywords");
                return Collections.emptyList();
            }

            String rawContent = response.getBody().extractContent();
            if (rawContent == null || rawContent.isBlank()) {
                log.warn("Groq returned empty content for hot keywords");
                return Collections.emptyList();
            }

            List<String> keywords = parseKeywordArray(rawContent);
            if (keywords.isEmpty()) {
                log.warn("Failed to parse hot keywords from Groq response. Raw: {}", rawContent);
                return Collections.emptyList();
            }

            List<String> normalized = keywords.stream()
                    .map(String::trim)
                    .filter(k -> !k.isEmpty())
                    .distinct()
                    .limit(count)
                    .toList();

            log.info("Groq returned {} hot keywords: {}", normalized.size(), normalized);
            return normalized;

        } catch (HttpClientErrorException e) {
            log.error("Groq API HTTP {} for hot keywords (model={}): {}",
                    e.getStatusCode().value(), model, e.getResponseBodyAsString());
            return Collections.emptyList();
        } catch (RestClientException e) {
            log.error("Groq API call failed for hot keywords (model={}): {}", model, e.getMessage());
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Unexpected error calling Groq for hot keywords (model={}): {}", model, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private List<String> fallbackKeywords(String keyword) {
        return Collections.singletonList(keyword.trim());
    }
}
