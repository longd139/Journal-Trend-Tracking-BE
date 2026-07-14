package com.sra.journal_tracking.dto.author;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lightweight DTO for author autocomplete suggestions from OpenAlex /authors API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthorSuggestionResponse {
    private String authorId;      // OpenAlex author ID (e.g. "https://openalex.org/A...")
    private String fullName;      // Author display name
    private String affiliation;   // Last known institution
    private Integer hIndex;       // h-index
    private Integer totalCitations; // Total citation count
    private Long paperCount;      // Total works count
}
