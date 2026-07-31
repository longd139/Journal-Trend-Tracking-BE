package com.sra.journal_tracking.dto.report;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateReportRequest {

    @NotBlank(message = "Report type is required")
    private String reportType; // PDF_ISSUE, CONTENT_ERROR, OTHER

    private String targetType; // PAPER, PDF, JOURNAL

    private UUID targetId;

    @NotBlank(message = "Title is required")
    @Size(max = 300, message = "Title must be at most 300 characters")
    private String title;

    @Size(max = 2000, message = "Description must be at most 2000 characters")
    private String description;
}
