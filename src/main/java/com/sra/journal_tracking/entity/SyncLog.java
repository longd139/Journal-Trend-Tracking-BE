package com.sra.journal_tracking.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "SYNC_LOG")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"source"})
@ToString(exclude = {"source"})
public class SyncLog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "LogID", updatable = false, nullable = false)
    private UUID logId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "SourceID", nullable = false)
    private ApiSource source;

    @Column(name = "SyncType", nullable = false, length = 15)
    private String syncType;

    @Column(name = "IsManual", nullable = false)
    @Builder.Default
    private Boolean isManual = false;

    @Column(name = "Status", nullable = false, length = 20)
    @Builder.Default
    private String status = "running";

    @Column(name = "PapersFetched")
    @Builder.Default
    private Integer papersFetched = 0;

    @Column(name = "PapersInserted")
    @Builder.Default
    private Integer papersInserted = 0;

    @Column(name = "ErrorMessage", columnDefinition = "NVARCHAR(MAX)")
    private String errorMessage;

    @Column(name = "StartedAt", nullable = false, updatable = false)
    private LocalDateTime startedAt;

    @Column(name = "CompletedAt")
    private LocalDateTime completedAt;

    @PrePersist
    protected void onCreate() {
        if (startedAt == null) {
            startedAt = LocalDateTime.now();
        }
    }
}