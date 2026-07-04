package com.sra.journal_tracking.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * AI client for DeepSeek API (OpenAI-compatible format).
 *
 * <pre>
 * POST https://api.deepseek.com/chat/completions
 * Authorization: Bearer sk-...
 * Body: {model, messages: [{role: "user", content: "..."}], max_tokens, temperature}
 * Response: {choices: [{message: {content: "..."}}]}
 * </pre>
 */
@Slf4j
@Service
public class DeepSeekClient implements AIClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${deepseek.api.url:https://api.deepseek.com/chat/completions}")
    private String apiUrl;

    @Value("${deepseek.api.key:}")
    private String apiKey;

    @Value("${deepseek.model:deepseek-chat}")
    private String model;

    public DeepSeekClient(@Qualifier("deepseekRestTemplate") RestTemplate restTemplate,
                          ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public String call(String prompt, int maxTokens, double temperature) throws Exception {
        if (apiKey == null || apiKey.isBlank()) {
            throw new RuntimeException("DeepSeek API key is not configured");
        }

        // ── Build request body (OpenAI format) ──
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);

        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> userMessage = new LinkedHashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", prompt);
        messages.add(userMessage);
        requestBody.put("messages", messages);

        requestBody.put("max_tokens", maxTokens);
        requestBody.put("temperature", temperature);

        // ── Send ──
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        log.debug("Calling DeepSeek API: {} (model={}, maxTokens={}, temp={})",
                apiUrl, model, maxTokens, temperature);

        ResponseEntity<String> response;
        try {
            response = restTemplate.exchange(apiUrl, HttpMethod.POST, request, String.class);
        } catch (RestClientException e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("401") || msg.contains("403")) {
                throw new RuntimeException("DeepSeek authentication failed. Check DEEPSEEK_API_KEY. Error: " + msg, e);
            }
            if (msg.contains("429")) {
                throw new RuntimeException("DeepSeek rate limit exceeded. Error: " + msg, e);
            }
            throw new RuntimeException("DeepSeek API error: " + msg, e);
        }

        if (response.getBody() == null) {
            throw new RuntimeException("DeepSeek returned empty response");
        }

        // ── Parse response (OpenAI format) ──
        JsonNode root = objectMapper.readTree(response.getBody());

        // Check for error response
        JsonNode error = root.path("error");
        if (!error.isMissingNode()) {
            String errorMsg = error.path("message").asText("Unknown error");
            throw new RuntimeException("DeepSeek API error: " + errorMsg);
        }

        JsonNode choices = root.path("choices");
        if (choices.isEmpty()) {
            throw new RuntimeException("DeepSeek returned no choices");
        }

        JsonNode message = choices.get(0).path("message");

        // Try "content" first, fall back to "reasoning_content" (reasoning models like deepseek-v4-pro)
        String text = message.path("content").asText("");
        if (text.isBlank()) {
            text = message.path("reasoning_content").asText("");
        }

        if (text.isBlank()) {
            throw new RuntimeException("DeepSeek returned empty content");
        }

        return text;
    }
}
