package com.sra.journal_tracking.controller;

import com.sra.journal_tracking.dto.response.AppResponse;
import com.sra.journal_tracking.dto.upgrade.CreateUpgradeRequest;
import com.sra.journal_tracking.dto.upgrade.UpgradeRequestDTO;
import com.sra.journal_tracking.service.UpgradeRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class UserUpgradeController {

    private final UpgradeRequestService upgradeRequestService;

    @PostMapping("/upgrade-request")
    @PreAuthorize("hasRole('ACADEMIC_USER')")
    public ResponseEntity<AppResponse<UpgradeRequestDTO>> submitUpgradeRequest(
            Authentication authentication,
            @Valid @RequestBody CreateUpgradeRequest request) {
        UpgradeRequestDTO result = upgradeRequestService.submitRequest(authentication.getName(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AppResponse.success("Upgrade request submitted", result));
    }

    @GetMapping("/upgrade-requests")
    @PreAuthorize("hasRole('ACADEMIC_USER')")
    public ResponseEntity<AppResponse<Page<UpgradeRequestDTO>>> getMyUpgradeRequests(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<UpgradeRequestDTO> requests = upgradeRequestService.getUserRequests(authentication.getName(), page, size);
        return ResponseEntity.ok(AppResponse.success("Upgrade requests retrieved", requests));
    }

    @GetMapping("/upgrade-requests/{id}")
    @PreAuthorize("hasRole('ACADEMIC_USER')")
    public ResponseEntity<AppResponse<UpgradeRequestDTO>> getMyUpgradeRequestDetail(
            Authentication authentication,
            @PathVariable UUID id) {
        UpgradeRequestDTO request = upgradeRequestService.getUserRequestDetail(authentication.getName(), id);
        return ResponseEntity.ok(AppResponse.success("Upgrade request detail retrieved", request));
    }
}
