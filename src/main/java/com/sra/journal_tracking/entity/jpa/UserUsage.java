package com.sra.journal_tracking.entity.jpa;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "USER_USAGE")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"user"})
@ToString(exclude = {"user"})
public class UserUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "UsageID", updatable = false, nullable = false)
    private UUID usageId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "UserID", nullable = false)
    private User user;

    @Column(name = "UsageMonth", nullable = false, length = 7)
    private String usageMonth;

    @Column(name = "SearchCount", nullable = false)
    @Builder.Default
    private Integer searchCount = 0;

    @Column(name = "ViewCount", nullable = false)
    @Builder.Default
    private Integer viewCount = 0;

    @Column(name = "ChartViewCount", nullable = false)
    @Builder.Default
    private Integer chartViewCount = 0;

    @Column(name = "LastUpdated", nullable = false)
    @Builder.Default
    private LocalDateTime lastUpdated = LocalDateTime.now();
}
