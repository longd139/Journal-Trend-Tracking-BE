package com.sra.journal_tracking.entity.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "USER_REPORT")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"user", "resolvedByAdmin"})
@ToString(exclude = {"user", "resolvedByAdmin"})
public class UserReport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ReportID", updatable = false, nullable = false)
    private UUID reportId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "UserID", nullable = false)
    private User user;

    @Convert(converter = ReportTypeConverter.class)
    @Column(name = "ReportType", nullable = false, length = 50)
    private ReportType reportType;

    @Column(name = "TargetType", length = 50)
    private String targetType;

    @Column(name = "TargetID")
    private UUID targetId;

    @Column(name = "Title", nullable = false, length = 300)
    private String title;

    @Column(name = "Description", columnDefinition = "NVARCHAR(MAX)")
    private String description;

    @Convert(converter = ReportStatusConverter.class)
    @Column(name = "Status", nullable = false, length = 20)
    @Builder.Default
    private ReportStatus status = ReportStatus.PENDING;

    @Column(name = "AdminNote", columnDefinition = "NVARCHAR(MAX)")
    private String adminNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ResolvedByAdminID")
    private User resolvedByAdmin;

    @Column(name = "CreatedAt", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "ResolvedAt")
    private LocalDateTime resolvedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
