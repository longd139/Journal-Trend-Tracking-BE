package com.sra.journal_tracking.dto.upgrade;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpgradeRequestDTO {
    private UUID requestId;
    private UUID userId;
    private String userEmail;
    private String userFullName;
    private String institution;
    private String researchField;
    private String position;
    private String orcid;
    private String reason;
    private String paperLinks;
    private String paperFileUrls;
    private String status;
    private String adminNote;
    private UUID reviewedBy;
    private String reviewedByName;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
}
