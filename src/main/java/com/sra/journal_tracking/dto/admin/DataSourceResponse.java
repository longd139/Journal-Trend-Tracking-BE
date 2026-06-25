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
public class DataSourceResponse {
    private UUID sourceId;
    private String sourceName;
    private String baseUrl;
    private Boolean isActive;
    private Integer rateLimitRpm;
    private LocalDateTime lastSyncedAt;
}
