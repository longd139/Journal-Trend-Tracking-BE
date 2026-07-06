package com.sra.journal_tracking.dto.trend;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for a ResearchTopic entity.
 * Used by the public research-topic REST endpoints for discovery and browsing.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResearchTopicResponse {

    /** Unique topic identifier. */
    private UUID topicId;

    /** Human-readable topic name (e.g. "Graph Neural Networks"). */
    private String topicName;

    /** Research field this topic belongs to. */
    private UUID fieldId;

    /** Research field name for display. */
    private String fieldName;

    /** Whether this topic is currently trending. */
    private Boolean isTrending;

    /** Trend score (0–100), higher = hotter trend. */
    private BigDecimal trendScore;

    /** Number of papers associated with this topic. */
    private Integer paperCount;

    /** When this topic record was last updated. */
    private LocalDateTime updatedAt;
}
