package com.sra.journal_tracking.entity.jpa;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "PUBLICATION_TREND")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublicationTrend {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "TrendID", updatable = false, nullable = false)
    private UUID trendId;

    @Column(name = "PeriodType", nullable = false, length = 10)
    private String periodType;

    @Column(name = "PeriodValue", nullable = false, length = 15)
    private String periodValue;

    @Column(name = "TrendTarget", nullable = false, length = 10)
    private String trendTarget;

    @Column(name = "TargetID", nullable = false)
    private UUID targetId;

    @Column(name = "PaperCount", nullable = false)
    @Builder.Default
    private Integer paperCount = 0;

    @Column(name = "CitationCount", nullable = false)
    @Builder.Default
    private Integer citationCount = 0;

    @Column(name = "GrowthRate", precision = 10, scale = 4)
    private BigDecimal growthRate;

    @Column(name = "CalculatedAt", nullable = false)
    @Builder.Default
    private LocalDateTime calculatedAt = LocalDateTime.now();
}
