package com.sra.journal_tracking.controller;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.sra.journal_tracking.dto.response.AppResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
public class BackgroundController {

    private final Cloudinary cloudinary;

    @Operation(summary = "Upload background image to Cloudinary")
    @PostMapping("/background")
    public ResponseEntity<AppResponse<Map<String, Object>>> uploadBackground(
            @RequestParam("file") MultipartFile file) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(AppResponse.of(400, "File is empty", null));
        }

        // Validate file type
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ResponseEntity.badRequest()
                    .body(AppResponse.of(400, "Only image files are allowed", null));
        }

        // Validate file size (max 5MB)
        if (file.getSize() > 5 * 1024 * 1024) {
            return ResponseEntity.badRequest()
                    .body(AppResponse.of(400, "File size must be under 5MB", null));
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "folder", "scitrack/backgrounds",
                            "resource_type", "image"
                    )
            );

            String url = (String) result.get("secure_url");
            log.info("Background uploaded: {}", url);

            return ResponseEntity.ok(AppResponse.success("Background uploaded",
                    Map.of("url", url, "publicId", result.get("public_id"))));

        } catch (IOException e) {
            log.error("Failed to upload background: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(AppResponse.of(500, "Upload failed: " + e.getMessage(), null));
        }
    }
}
