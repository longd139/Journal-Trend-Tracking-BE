package com.sra.journal_tracking.entity.jpa;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "SEARCH_KEYWORD")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SearchKeyword {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "SearchKeywordID", updatable = false, nullable = false)
    private UUID searchKeywordId;

    @Column(name = "KeywordText", nullable = false, length = 500)
    private String keywordText;

    @Column(name = "NormalizedText", nullable = false, length = 500, unique = true)
    private String normalizedText;

    @Column(name = "SearchCount", nullable = false)
    @Builder.Default
    private Integer searchCount = 1;

    @Column(name = "LastSearchedAt", nullable = false)
    private LocalDateTime lastSearchedAt;

    @Column(name = "CreatedAt", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (lastSearchedAt == null) {
            lastSearchedAt = LocalDateTime.now();
        }
    }
}
