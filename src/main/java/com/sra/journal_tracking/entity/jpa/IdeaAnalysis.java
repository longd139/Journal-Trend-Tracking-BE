package com.sra.journal_tracking.entity.jpa;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Stores the complete result of an Idea Analysis pipeline run.
 * The full response JSON (including gapAnalysis, literatureReview, papers)
 * is persisted in {@code resultJson} so History views load it without re-running AI.
 */
@Entity
@Table(name = "IDEA_ANALYSIS")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"user"})
@ToString(exclude = {"user"})
public class IdeaAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "analysis_id", updatable = false, nullable = false)
    private UUID analysisId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "idea_text", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String ideaText;

    /**
     * MD5 hash of {@code ideaText} (lowercased + trimmed).
     * Used for cache lookup — identical ideas from different users can share
     * paper evaluations via {@link PaperEvaluationCache}.
     */
    @Column(name = "idea_hash", nullable = false, length = 64)
    private String ideaHash;

    /**
     * JSON array of keyword strings used for the search.
     * Example: {@code ["deep learning","lung cancer","CT imaging"]}
     */
    @Column(name = "keywords", columnDefinition = "NVARCHAR(MAX)")
    private String keywords;

    /**
     * Full analysis result serialized as JSON.
     * Includes: papers[], gapAnalysis{}, literatureReview{}
     */
    @Column(name = "result_json", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String resultJson;

    @Column(name = "paper_count")
    @Builder.Default
    private Integer paperCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (paperCount == null) {
            paperCount = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
