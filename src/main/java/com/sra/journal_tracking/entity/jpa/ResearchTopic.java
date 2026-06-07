package com.sra.journal_tracking.entity.jpa;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "RESEARCH_TOPIC")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"field"})
@ToString(exclude = {"field"})
public class ResearchTopic {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "TopicID", updatable = false, nullable = false)
    private UUID topicId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "FieldID")
    private ResearchField field;

    @Column(name = "TopicName", nullable = false, length = 300)
    private String topicName;

    @Column(name = "IsTrending", nullable = false)
    @Builder.Default
    private Boolean isTrending = false;

    @Column(name = "TrendScore", nullable = false, precision = 10, scale = 4)
    @Builder.Default
    private BigDecimal trendScore = BigDecimal.ZERO;

    @Column(name = "PaperCount", nullable = false)
    @Builder.Default
    private Integer paperCount = 0;

    @Column(name = "UpdatedAt", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}
