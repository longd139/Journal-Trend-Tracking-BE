package com.sra.journal_tracking.controller;

import com.sra.journal_tracking.entity.jpa.SyncLog;
import com.sra.journal_tracking.service.DataSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/sync")
@RequiredArgsConstructor
@Tag(name = "Data Sync (Admin)", description = "APIs for manual data synchronization")
@SecurityRequirement(name = "Bearer Authentication")
public class DataSyncController {

    private final DataSyncService dataSyncService;

    @Operation(summary = "Manual trigger OpenAlex Sync", description = "Fetch papers from OpenAlex based on keyword")
    @PostMapping("/openalex")
    public ResponseEntity<String> triggerOpenAlexSync(
            @RequestParam(defaultValue = "machine learning") String query,
            @RequestParam(defaultValue = "10") int limit) {
        SyncLog syncLog = dataSyncService.syncFromOpenAlex(query, limit);
        return buildSyncResponse("OpenAlex", query, syncLog);
    }

    private ResponseEntity<String> buildSyncResponse(String sourceName, String query, SyncLog syncLog) {
        String message = String.format(
                "%s sync %s for query: %s. Fetched: %d, Inserted: %d%s",
                sourceName,
                syncLog.getStatus(),
                query,
                syncLog.getPapersFetched(),
                syncLog.getPapersInserted(),
                syncLog.getErrorMessage() != null ? ". Error: " + syncLog.getErrorMessage() : ""
        );

        if ("failed".equalsIgnoreCase(syncLog.getStatus())) {
            return ResponseEntity.internalServerError().body(message);
        }

        return ResponseEntity.ok(message);
    }
}
