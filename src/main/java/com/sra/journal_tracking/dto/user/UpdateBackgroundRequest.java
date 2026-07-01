package com.sra.journal_tracking.dto.user;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateBackgroundRequest {

    @Size(max = 500, message = "backgroundUrl must be at most 500 characters")
    private String backgroundUrl;
}
