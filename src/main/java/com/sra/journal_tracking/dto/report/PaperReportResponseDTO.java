package com.sra.journal_tracking.dto.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaperReportResponseDTO {

    private UUID reportId;
    private UUID paperId;
    private String reason;
    private String description;
    private List<String> imageUrls;
    private String status;
    private LocalDateTime createdAt;
}
