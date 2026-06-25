package com.sra.journal_tracking.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncTriggerResponse {
    private String sourceName;
    private String query;
    private Integer limit;
    private Integer yearFrom;
    private Integer yearTo;
    private String status;
    private String message;
}
