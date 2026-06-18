package com.sra.journal_tracking.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for deserializing Gemini API generateContent response.
 * Only maps the fields we need (candidates[].content.parts[].text).
 */
@Data
@NoArgsConstructor
public class GeminiResponseDTO {

    private List<Candidate> candidates;

    @Data
    @NoArgsConstructor
    public static class Candidate {
        private Content content;
    }

    @Data
    @NoArgsConstructor
    public static class Content {
        private List<Part> parts;
    }

    @Data
    @NoArgsConstructor
    public static class Part {
        private String text;
    }

    /**
     * Extracts the first candidate's text content, or null if no candidates.
     */
    public String extractText() {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        Candidate candidate = candidates.get(0);
        if (candidate.getContent() == null || candidate.getContent().getParts() == null
                || candidate.getContent().getParts().isEmpty()) {
            return null;
        }
        return candidate.getContent().getParts().get(0).getText();
    }
}
