package com.sra.journal_tracking.controller;

import com.sra.journal_tracking.dto.admin.SyncHistoryResponse;
import com.sra.journal_tracking.dto.admin.SyncTriggerRequest;
import com.sra.journal_tracking.dto.admin.SyncTriggerResponse;
import com.sra.journal_tracking.dto.response.AppResponse;
import com.sra.journal_tracking.service.AdminService;
import com.sra.journal_tracking.entity.jpa.Notification;
import com.sra.journal_tracking.entity.jpa.NotificationType;
import com.sra.journal_tracking.entity.jpa.User;
import com.sra.journal_tracking.repository.jpa.NotificationRepository;
import com.sra.journal_tracking.repository.jpa.UserRepository;
import com.sra.journal_tracking.service.JournalEnrichmentService;
import com.sra.journal_tracking.service.NotificationEventPublisher;
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
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/admin/sync")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminSyncController {

    private final AdminService adminService;
    private final JournalEnrichmentService journalEnrichmentService;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationEventPublisher eventPublisher;

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

    @PostMapping("/enrich-journals")
    public ResponseEntity<AppResponse<String>> enrichJournals() {
        String result = journalEnrichmentService.enrichJournals();
        return ResponseEntity.ok(AppResponse.success(result));
    }

    @PostMapping("/enrich-journals/upload")
    public ResponseEntity<AppResponse<String>> uploadEnrichCsv(
            @RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(AppResponse.of(400, "File is empty", null));
        }
        try {
            String result = journalEnrichmentService.enrichFromCsv(file.getInputStream());
            notifyAdminsEnrichmentComplete(result);
            return ResponseEntity.ok(AppResponse.success(result));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(AppResponse.of(500, "Failed to process CSV: " + e.getMessage(), null));
        }
    }

    private void notifyAdminsEnrichmentComplete(String result) {
        List<User> admins = userRepository.findByRole_RoleName("admin");
        if (admins.isEmpty()) return;
        for (User admin : admins) {
            Notification notification = notificationRepository.save(Notification.builder()
                    .user(admin)
                    .type(NotificationType.SYSTEM)
                    .title("SCImago Enrichment Complete")
                    .message(result)
                    .isRead(false)
                    .build());
            try {
                eventPublisher.publish(admin.getUserId(), notification);
            } catch (Exception ignored) { /* best-effort */ }
        }
    }
}