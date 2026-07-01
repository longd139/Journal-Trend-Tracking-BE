package com.sra.journal_tracking.dto.pdf;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PdfCandidateResponse {
    private String source;
    private String pdfUrl;
    private String landingPageUrl;
    private Boolean isOpenAccess;
}
