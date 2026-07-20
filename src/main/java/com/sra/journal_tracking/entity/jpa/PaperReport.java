package com.sra.journal_tracking.entity.jpa;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "PAPER_REPORT")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"user"})
@ToString(exclude = {"user"})
public class PaperReport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ReportID", updatable = false, nullable = false)
    private UUID reportId;

    @Column(name = "PaperID", nullable = false)
    private UUID paperId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "UserID", nullable = false)
    private User user;

    @Column(name = "Reason", nullable = false, length = 50)
    private String reason;

    @Column(name = "Description", columnDefinition = "NVARCHAR(MAX)")
    private String description;

    @Column(name = "ImageUrls", columnDefinition = "NVARCHAR(MAX)")
    private String imageUrls;

    @Column(name = "Status", nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "CreatedAt", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UpdatedAt")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
