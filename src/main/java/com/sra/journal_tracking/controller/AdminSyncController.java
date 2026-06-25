package com.sra.journal_tracking.controller;

import com.sra.journal_tracking.dto.admin.SyncHistoryResponse;
import com.sra.journal_tracking.dto.admin.SyncTriggerRequest;
import com.sra.journal_tracking.dto.admin.SyncTriggerResponse;
import com.sra.journal_tracking.dto.response.AppResponse;
import com.sra.journal_tracking.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/sync")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminSyncController {

    private final AdminService adminService;

    @PostMapping("/trigger")
    public ResponseEntity<AppResponse<SyncTriggerResponse>> triggerManualSync(
            @RequestBody(required = false) SyncTriggerRequest request) {
        SyncTriggerResponse response = adminService.triggerManualSync(
                request != null ? request : new SyncTriggerRequest());
        return ResponseEntity.accepted()
                .body(AppResponse.of(202, "Manual sync request accepted", response));
    }

    @GetMapping("/history")
    public ResponseEntity<AppResponse<Page<SyncHistoryResponse>>> getSyncHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean manual) {
        return ResponseEntity.ok(AppResponse.success(
                "Sync history retrieved",
                adminService.getSyncHistory(page, size, status, manual)));
    }
}
