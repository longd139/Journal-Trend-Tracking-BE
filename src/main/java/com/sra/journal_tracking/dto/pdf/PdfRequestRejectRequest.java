package com.sra.journal_tracking.dto.pdf;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PdfRequestRejectRequest {

    @Size(max = 1000, message = "adminNote must be at most 1000 characters")
    private String adminNote;
}
