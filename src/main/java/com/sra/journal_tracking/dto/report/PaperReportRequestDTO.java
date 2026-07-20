package com.sra.journal_tracking.dto.report;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaperReportRequestDTO {

    @NotBlank(message = "Reason is required")
    private String reason;

    @Size(max = 2000, message = "Description must be under 2000 characters")
    private String description;
}
