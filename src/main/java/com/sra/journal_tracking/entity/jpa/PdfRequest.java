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
@Table(name = "PDF_REQUEST")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"user", "paper", "resolvedByAdmin"})
@ToString(exclude = {"user", "paper", "resolvedByAdmin"})
public class PdfRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "RequestID", updatable = false, nullable = false)
    private UUID requestId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "UserID", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PaperID", nullable = false)
    private ResearchPaper paper;

    @Convert(converter = PdfRequestStatusConverter.class)
    @Column(name = "Status", nullable = false, length = 20)
    @Builder.Default
    private PdfRequestStatus status = PdfRequestStatus.PENDING;

    @Column(name = "UserMessage", length = 1000)
    private String userMessage;

    @Column(name = "AdminNote", length = 1000)
    private String adminNote;

    @Column(name = "RequestedAt", nullable = false, updatable = false)
    private LocalDateTime requestedAt;

    @Column(name = "ResolvedAt")
    private LocalDateTime resolvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ResolvedByAdminID")
    private User resolvedByAdmin;

    @PrePersist
    protected void onCreate() {
        if (requestedAt == null) {
            requestedAt = LocalDateTime.now();
        }
        if (status == null) {
            status = PdfRequestStatus.PENDING;
        }
    }
}
