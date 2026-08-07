package com.sra.journal_tracking.controller;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sra.journal_tracking.dto.response.AppResponse;
import com.sra.journal_tracking.dto.upgrade.CreateUpgradeRequest;
import com.sra.journal_tracking.dto.upgrade.UpgradeRequestDTO;
import com.sra.journal_tracking.entity.jpa.NotificationType;
import com.sra.journal_tracking.service.AdminNotificationService;
import com.sra.journal_tracking.service.UpgradeRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class UserUpgradeController {

    private final UpgradeRequestService upgradeRequestService;
    private final AdminNotificationService adminNotificationService;
    private final Cloudinary cloudinary;
    private final ObjectMapper objectMapper;

    @PostMapping(value = "/upgrade-request", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ACADEMIC_USER')")
    public ResponseEntity<AppResponse<UpgradeRequestDTO>> submitUpgradeRequest(
            Authentication authentication,
            @Valid @RequestPart("data") CreateUpgradeRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) {

        // Upload PDF files to Cloudinary
        if (files != null && !files.isEmpty()) {
            List<String> urls = new ArrayList<>();
            for (MultipartFile file : files) {
                if (file.isEmpty()) continue;
                File tempFile = null;
                try {
                    // Save to temp file first to avoid byte[] encoding issues
                    Path tempDir = Files.createTempDirectory("scitrack-upgrade-");
                    tempFile = new File(tempDir.toFile(), file.getOriginalFilename() != null
                            ? file.getOriginalFilename()
                            : "upload.pdf");
                    file.transferTo(tempFile);

                    @SuppressWarnings("unchecked")
                    Map<String, Object> result = cloudinary.uploader().uploadLarge(
                            tempFile,
                            ObjectUtils.asMap(
                                    "folder", "scitrack/upgrade-requests",
                                    "resource_type", "auto"
                            )
                    );
                    String url = (String) result.get("secure_url");
                    // Add fl_attachment flag so browser recognizes it as a proper PDF
                    url = url.replace("/upload/", "/upload/fl_attachment/");
                    urls.add(url);
                } catch (IOException e) {
                    log.warn("Failed to upload upgrade request file: {}", e.getMessage());
                } finally {
                    if (tempFile != null && tempFile.exists()) {
                        try {
                            Path tempDir = tempFile.getParentFile().toPath();
                            Files.deleteIfExists(tempFile.toPath());
                            Files.deleteIfExists(tempDir);
                        } catch (IOException e) {
                            log.warn("Failed to clean up temp file: {}", e.getMessage());
                        }
                    }
                }
            }
            if (!urls.isEmpty()) {
                try {
                    request.setPaperFileUrls(objectMapper.writeValueAsString(urls));
                } catch (Exception e) {
                    log.warn("Failed to serialize file URLs: {}", e.getMessage());
                }
            }
        }

        UpgradeRequestDTO result = upgradeRequestService.submitRequest(authentication.getName(), request);

        // Notify admins about the new upgrade request
        try {
            adminNotificationService.broadcastToAdmins(
                    NotificationType.SYSTEM_ALERT,
                    "New Upgrade Request",
                    "User " + authentication.getName() + " has requested to upgrade to Researcher."
            );
        } catch (Exception e) {
            // Best-effort: don't fail the request if notification fails
        }

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
