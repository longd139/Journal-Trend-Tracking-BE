package com.sra.journal_tracking.entity.jpa;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Caches computed keyword trend report JSON in the database.
 * Upserted every time a keyword trend report is generated.
 * Provides persistence-backed storage beyond the in-memory @Cacheable layer.
 */
@Entity
@Table(name = "KEYWORD_TREND_CACHE")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KeywordTrendCache {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "CacheID", updatable = false, nullable = false)
    private UUID cacheId;

    @Column(name = "Keyword", nullable = false, length = 500)
    private String keyword;

    @Column(name = "NormalizedKeyword", nullable = false, length = 500, unique = true)
    private String normalizedKeyword;

    @Column(name = "ReportData", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String reportData;

    @Column(name = "CreatedAt", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UpdatedAt", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
