package com.sra.journal_tracking.dto.admin;

import lombok.Data;

@Data
public class DataSourceRequest {
    private String sourceName;
    private String baseUrl;
    private Boolean isActive;
    private Integer rateLimitRpm;
}
