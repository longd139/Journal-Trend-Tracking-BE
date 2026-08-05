package com.sra.journal_tracking.controller;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.sra.journal_tracking.dto.pdf.PdfCandidateSearchResponse;
import com.sra.journal_tracking.dto.pdf.PdfRequestFulfillRequest;
import com.sra.journal_tracking.dto.pdf.PdfRequestRejectRequest;
import com.sra.journal_tracking.dto.pdf.PdfRequestResponse;
import com.sra.journal_tracking.dto.response.AppResponse;
import com.sra.journal_tracking.service.PdfRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/admin/pdf-requests")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminPdfRequestController {

    private final PdfRequestService pdfRequestService;
    private final Cloudinary cloudinary;

    @GetMapping
    public ResponseEntity<AppResponse<List<PdfRequestResponse>>> getRequests(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(AppResponse.success(
                "PDF requests retrieved",
                pdfRequestService.getAdminRequests(status, page, size)));
    }

    @PostMapping("/{requestId}/find-candidates")
    public ResponseEntity<AppResponse<PdfCandidateSearchResponse>> findCandidates(
            @PathVariable UUID requestId) {
        return ResponseEntity.ok(AppResponse.success(
                "PDF candidates retrieved",
                pdfRequestService.findCandidates(requestId)));
    }

    @PostMapping("/{requestId}/upload")
    public ResponseEntity<AppResponse<Map<String, Object>>> uploadPdf(
            @PathVariable UUID requestId,
            @RequestParam("file") MultipartFile file) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(AppResponse.of(400, "File is empty", null));
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.equals("application/pdf")) {
            return ResponseEntity.badRequest()
                    .body(AppResponse.of(400, "Only PDF files are allowed", null));
        }

        if (file.getSize() > 10 * 1024 * 1024) {
            return ResponseEntity.badRequest()
                    .body(AppResponse.of(400, "File size must be under 10MB", null));
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "folder", "scitrack/pdf-requests",
                            "resource_type", "raw"
                    )
            );

            String url = (String) result.get("secure_url");
            log.info("PDF uploaded for request {}: {}", requestId, url);

            return ResponseEntity.ok(AppResponse.success("PDF uploaded",
                    Map.of("url", url, "publicId", result.get("public_id"))));

        } catch (IOException e) {
            log.error("Failed to upload PDF: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(AppResponse.of(500, "Upload failed: " + e.getMessage(), null));
        }
    }

    @PutMapping("/{requestId}/fulfill")
    public ResponseEntity<AppResponse<PdfRequestResponse>> fulfill(
            @PathVariable UUID requestId,
            Authentication authentication,
            @Valid @RequestBody PdfRequestFulfillRequest request) {
        return ResponseEntity.ok(AppResponse.success(
                "PDF request fulfilled",
                pdfRequestService.fulfillRequest(requestId, authentication.getName(), request)));
    }

    @PutMapping("/{requestId}/reject")
    public ResponseEntity<AppResponse<PdfRequestResponse>> reject(
            @PathVariable UUID requestId,
            Authentication authentication,
            @Valid @RequestBody(required = false) PdfRequestRejectRequest request) {
        return ResponseEntity.ok(AppResponse.success(
                "PDF request rejected",
                pdfRequestService.rejectRequest(requestId, authentication.getName(), request)));
    }
}
