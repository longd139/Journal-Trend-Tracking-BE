package com.sra.journal_tracking.entity.jpa;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ROLE_UPGRADE_REQUEST")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"user", "reviewedBy"})
@ToString(exclude = {"user", "reviewedBy"})
public class RoleUpgradeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "RequestID", updatable = false, nullable = false)
    private UUID requestId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "UserID", nullable = false)
    private User user;

    @Column(name = "FullName", nullable = false, length = 200)
    private String fullName;

    @Column(name = "Institution", nullable = false, length = 300)
    private String institution;

    @Column(name = "ResearchField", nullable = false, length = 200)
    private String researchField;

    @Column(name = "Position", nullable = false, length = 100)
    private String position;

    @Column(name = "Orcid", length = 50)
    private String orcid;

    @Column(name = "Reason", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "Status", nullable = false, length = 20)
    @Builder.Default
    private UpgradeRequestStatus status = UpgradeRequestStatus.PENDING;

    @Column(name = "AdminNote", length = 500)
    private String adminNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ReviewedBy")
    private User reviewedBy;

    @Column(name = "ReviewedAt")
    private LocalDateTime reviewedAt;

    @Column(name = "CreatedAt", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
