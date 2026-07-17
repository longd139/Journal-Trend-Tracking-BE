package com.sra.journal_tracking.controller;

import com.sra.journal_tracking.dto.response.AppResponse;
import com.sra.journal_tracking.dto.upgrade.ApproveRejectRequest;
import com.sra.journal_tracking.dto.upgrade.UpgradeRequestDTO;
import com.sra.journal_tracking.service.UpgradeRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/upgrade-requests")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUpgradeController {

    private final UpgradeRequestService upgradeRequestService;

    @GetMapping
    public ResponseEntity<AppResponse<Page<UpgradeRequestDTO>>> getUpgradeRequests(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<UpgradeRequestDTO> requests = upgradeRequestService.getAdminRequests(status, page, size);
        return ResponseEntity.ok(AppResponse.success("Upgrade requests retrieved", requests));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AppResponse<UpgradeRequestDTO>> getUpgradeRequestDetail(@PathVariable UUID id) {
        UpgradeRequestDTO request = upgradeRequestService.getAdminRequestDetail(id);
        return ResponseEntity.ok(AppResponse.success("Upgrade request detail retrieved", request));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<AppResponse<UpgradeRequestDTO>> approveRequest(
            @PathVariable UUID id,
            Authentication authentication,
            @RequestBody(required = false) ApproveRejectRequest request) {
        UpgradeRequestDTO result = upgradeRequestService.approveRequest(id, authentication.getName(), request);
        return ResponseEntity.ok(AppResponse.success("Upgrade request approved", result));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<AppResponse<UpgradeRequestDTO>> rejectRequest(
            @PathVariable UUID id,
            Authentication authentication,
            @RequestBody(required = false) ApproveRejectRequest request) {
        UpgradeRequestDTO result = upgradeRequestService.rejectRequest(id, authentication.getName(), request);
        return ResponseEntity.ok(AppResponse.success("Upgrade request rejected", result));
    }
}
