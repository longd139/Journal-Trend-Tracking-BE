package com.sra.journal_tracking.dto.idea;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtractKeywordsRequest {
    @NotBlank(message = "Idea text cannot be empty")
    private String ideaText;
}
