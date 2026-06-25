package com.sra.journal_tracking.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemConfigResponse {
    private UUID configId;
    private String configKey;
    private String configValue;
    private String description;
    private LocalDateTime updatedAt;
    private UUID updatedBy;
}
