package com.sra.journal_tracking.entity.jpa;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "AUTO_SYNC_KEYWORD")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AutoSyncKeyword {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "KeywordID", updatable = false, nullable = false)
    private UUID keywordId;

    @Column(name = "Keyword", nullable = false, length = 500)
    private String keyword;

    @Column(name = "IntervalMinutes", nullable = false)
    @Builder.Default
    private Integer intervalMinutes = 60;

    @Column(name = "Enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Column(name = "LastSyncedAt")
    private LocalDateTime lastSyncedAt;

    @Column(name = "CreatedAt", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
