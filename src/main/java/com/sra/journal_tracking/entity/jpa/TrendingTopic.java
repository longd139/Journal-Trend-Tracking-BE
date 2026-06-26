package com.sra.journal_tracking.entity.jpa;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "TRENDING_TOPIC")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrendingTopic {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "TrendingTopicID", updatable = false, nullable = false)
    private UUID trendingTopicId;

    @Column(name = "TopicName", nullable = false, length = 300)
    private String topicName;

    @Column(name = "PaperCount", nullable = false)
    @Builder.Default
    private Integer paperCount = 0;

    @Column(name = "Source", nullable = false, length = 50)
    private String source;

    @Column(name = "DisplayOrder", nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;

    @Column(name = "UpdatedAt", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
