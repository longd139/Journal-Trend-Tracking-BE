package com.sra.journal_tracking.dto.upgrade;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApproveRejectRequest {

    @Size(max = 500)
    private String adminNote;
}
