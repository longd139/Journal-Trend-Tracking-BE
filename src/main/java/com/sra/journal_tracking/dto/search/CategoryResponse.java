package com.sra.journal_tracking.dto.search;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryResponse {

    /** The broad research field name (e.g., "Artificial Intelligence") */
    private String keywordText;

    /** Normalized text for backend queries */
    private String normalizedText;

    /** Number of papers connected to this keyword in Neo4j */
    private Long paperCount;
}
