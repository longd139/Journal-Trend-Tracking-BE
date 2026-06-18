package com.sra.journal_tracking.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for OpenAI-compatible chat completion response (Groq, etc.)
 */
@Data
@NoArgsConstructor
public class GroqResponseDTO {

    private List<Choice> choices;

    @Data
    @NoArgsConstructor
    public static class Choice {
        private Message message;
    }

    @Data
    @NoArgsConstructor
    public static class Message {
        private String content;
    }

    /**
     * Extracts the first choice's message content, or null if none.
     */
    public String extractContent() {
        if (choices == null || choices.isEmpty()) {
            return null;
        }
        Choice choice = choices.get(0);
        if (choice.getMessage() == null) {
            return null;
        }
        return choice.getMessage().getContent();
    }
}
