package com.sra.journal_tracking.dto.report;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateReportStatusRequest {

    @NotBlank(message = "Status is required")
    private String status; // reviewed, resolved, dismissed

    private String adminNote;
}
