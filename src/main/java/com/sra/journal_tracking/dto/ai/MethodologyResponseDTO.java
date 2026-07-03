package com.sra.journal_tracking.dto.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MethodologyResponseDTO {
    private UUID paperId;
    private String title;
    /** Extracted methodology, e.g. "quantitative survey", "RCT". Null when Gemini unavailable. */
    private String methodology;
}
