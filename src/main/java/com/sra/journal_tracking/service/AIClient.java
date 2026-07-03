package com.sra.journal_tracking.service;

/**
 * Abstraction for AI model API calls (DeepSeek, Gemini, OpenAI, etc.).
 * Each implementation handles its own API format while exposing a uniform interface.
 */
public interface AIClient {

    /**
     * Call the AI model with a text prompt.
     *
     * @param prompt       the prompt text to send
     * @param maxTokens    maximum output tokens
     * @param temperature  generation temperature (0.0–1.0)
     * @return the model's text response
     * @throws Exception  on any API error (network, quota, auth) — callers must handle gracefully
     */
    String call(String prompt, int maxTokens, double temperature) throws Exception;
}
