package com.sra.journal_tracking.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "API_SOURCE")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiSource {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "SourceID", updatable = false, nullable = false)
    private UUID sourceId;

    @Column(name = "SourceName", nullable = false, length = 100, unique = true)
    private String sourceName;

    @Column(name = "BaseURL", nullable = false, length = 500)
    private String baseUrl;

    @Column(name = "IsActive", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "RateLimitRPM")
    private Integer rateLimitRpm;

    @Column(name = "LastSyncedAt")
    private LocalDateTime lastSyncedAt;
}