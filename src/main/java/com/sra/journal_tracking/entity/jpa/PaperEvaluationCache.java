package com.sra.journal_tracking.entity.jpa;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Cross-user cache for paper evaluations.
 * When two users analyze ideas with the same {@code ideaHash},
 * the per-paper AI evaluation result is reused.
 * Cleaned up by scheduled job after 7 days.
 */
@Entity
@Table(name = "PAPER_EVALUATION_CACHE",
       uniqueConstraints = @UniqueConstraint(columnNames = {"paper_id", "idea_hash"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaperEvaluationCache {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "cache_id", updatable = false, nullable = false)
    private UUID cacheId;

    @Column(name = "paper_id", nullable = false)
    private UUID paperId;

    /**
     * MD5 hash of the (lowercased + trimmed) idea text.
     * Combined with {@code paperId} to form a unique cache key.
     */
    @Column(name = "idea_hash", nullable = false, length = 64)
    private String ideaHash;

    /**
     * JSON array of criteria evaluation results.
     * Example: {@code [{"criterionName":"TOPIC_MATCH","value":true,"evidenceQuote":"..."}]}
     */
    @Column(name = "criteria_json", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String criteriaJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
