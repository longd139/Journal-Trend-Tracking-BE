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
public class SyncHistoryResponse {
    private UUID logId;
    private UUID sourceId;
    private String sourceName;
    private String syncType;
    private Boolean isManual;
    private String status;
    private Integer papersFetched;
    private Integer papersInserted;
    private Integer papersUpdated;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
}
