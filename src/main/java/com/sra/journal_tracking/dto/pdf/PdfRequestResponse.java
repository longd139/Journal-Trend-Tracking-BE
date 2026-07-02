package com.sra.journal_tracking.dto.pdf;

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
public class PdfRequestResponse {
    private UUID requestId;
    private String status;
    private String userMessage;
    private String adminNote;
    private LocalDateTime requestedAt;
    private LocalDateTime resolvedAt;
    private UUID paperId;
    private String paperTitle;
    private String doi;
    private String pdfUrl;
    private Boolean pdfAvailable;
    private UUID journalId;
    private String journalName;
    private UUID requestedByUserId;
    private String requestedByEmail;
    private String requestedByName;
    private UUID resolvedByAdminId;
    private String resolvedByAdminEmail;
}
