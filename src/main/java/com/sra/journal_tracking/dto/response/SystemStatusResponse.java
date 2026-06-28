package com.sra.journal_tracking.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SystemStatusResponse {

    private String status;
    private String application;
    private String version;
    private boolean databaseConnected;
    private boolean neo4jConnected;
    private String uptime;
}
