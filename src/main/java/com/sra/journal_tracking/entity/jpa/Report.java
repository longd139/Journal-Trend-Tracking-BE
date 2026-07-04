package com.sra.journal_tracking.entity.jpa;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "REPORT")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"user", "field"})
@ToString(exclude = {"user", "field"})
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ReportID", updatable = false, nullable = false)
    private UUID reportId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "UserID", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "FieldID")
    private ResearchField field;

    @Column(name = "ReportName", nullable = false, length = 300)
    private String reportName;

    @Column(name = "PeriodStart", nullable = false)
    private LocalDate periodStart;

    @Column(name = "PeriodEnd", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "Status", nullable = false, length = 20)
    @Builder.Default
    private String status = "generating";

    @Column(name = "Format", nullable = false, length = 5)
    @Builder.Default
    private String format = "pdf";

    @Column(name = "FileURL", length = 1000)
    private String fileUrl;

    @Column(name = "CreatedAt", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
