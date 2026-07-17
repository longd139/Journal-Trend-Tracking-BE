package com.sra.journal_tracking.dto.idea;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdeaAnalysisRequest {
    @NotBlank(message = "Idea text cannot be empty")
    private String ideaText;

    @NotEmpty(message = "At least one keyword is required")
    private List<String> selectedKeywords;
}
