package com.sra.journal_tracking.dto.admin;

import lombok.Data;

@Data
public class SyncTriggerRequest {
    private String sourceName;
    private String query;
    private Integer limit;
    private Integer yearFrom;
    private Integer yearTo;
}
