package com.sra.journal_tracking.dto.report;

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
public class UserReportResponse {

    private UUID reportId;
    private UUID userId;
    private String userEmail;
    private String userFullName;
    private String reportType;
    private String targetType;
    private UUID targetId;
    private String title;
    private String description;
    private String status;
    private String adminNote;
    private UUID resolvedByAdminId;
    private String resolvedByAdminName;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
}
