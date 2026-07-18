package com.sra.journal_tracking.dto.upgrade;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateUpgradeRequest {

    @NotBlank(message = "Full name is required")
    @Size(max = 200)
    private String fullName;

    @NotBlank(message = "Institution is required")
    @Size(max = 300)
    private String institution;

    @NotBlank(message = "Research field is required")
    @Size(max = 200)
    private String researchField;

    @NotBlank(message = "Position is required")
    @Size(max = 100)
    private String position;

    @Size(max = 50)
    private String orcid;

    @NotBlank(message = "Reason is required")
    @Size(max = 5000)
    private String reason;
}
