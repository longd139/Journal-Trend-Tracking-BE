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
import org.springframework.http.MediaType;
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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    private static final long MAX_PDF_SIZE = 20 * 1024 * 1024; // 20MB

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

    @PutMapping("/{requestId}/fulfill")
    public ResponseEntity<AppResponse<PdfRequestResponse>> fulfill(
            @PathVariable UUID requestId,
            Authentication authentication,
            @Valid @RequestBody PdfRequestFulfillRequest request) {
        return ResponseEntity.ok(AppResponse.success(
                "PDF request fulfilled",
                pdfRequestService.fulfillRequest(requestId, authentication.getName(), request)));
    }

    @PostMapping(value = "/{requestId}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AppResponse<PdfRequestResponse>> uploadPdf(
            @PathVariable UUID requestId,
            Authentication authentication,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "adminNote", required = false) String adminNote) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(AppResponse.of(400, "File is empty", null));
        }

        // Validate file type
        String contentType = file.getContentType();
        if (contentType == null || !"application/pdf".equals(contentType)) {
            return ResponseEntity.badRequest()
                    .body(AppResponse.of(400, "Only PDF files are accepted", null));
        }

        // Validate file size (max 20MB)
        if (file.getSize() > MAX_PDF_SIZE) {
            return ResponseEntity.badRequest()
                    .body(AppResponse.of(400, "File size must be under 20MB", null));
        }

        File tempFile = null;
        try {
            // Save MultipartFile to temp file first (avoids byte[] encoding issues with Cloudinary SDK)
            Path tempDir = Files.createTempDirectory("scitrack-pdf-");
            tempFile = new File(tempDir.toFile(), file.getOriginalFilename() != null
                    ? file.getOriginalFilename()
                    : "upload.pdf");
            file.transferTo(tempFile);
            log.info("Temp file created: {} (size={} bytes)", tempFile.getAbsolutePath(), tempFile.length());

            // Upload PDF to Cloudinary using uploadLarge for reliable raw file handling
            @SuppressWarnings("unchecked")
            Map<String, Object> result = cloudinary.uploader().uploadLarge(
                    tempFile,
                    ObjectUtils.asMap(
                            "folder", "scitrack/pdfs",
                            "resource_type", "raw"
                    )
            );

            String cloudinaryUrl = (String) result.get("secure_url");
            // Add fl_attachment flag so browser recognizes it as a proper PDF
            cloudinaryUrl = cloudinaryUrl.replace("/upload/", "/upload/fl_attachment/");
            log.info("PDF uploaded to Cloudinary: {} (requestId={})", cloudinaryUrl, requestId);

            // Fulfill the request with the Cloudinary URL
            PdfRequestFulfillRequest fulfillRequest = PdfRequestFulfillRequest.builder()
                    .pdfUrl(cloudinaryUrl)
                    .adminNote(adminNote)
                    .build();

            PdfRequestResponse response = pdfRequestService.fulfillRequest(
                    requestId, authentication.getName(), fulfillRequest);

            return ResponseEntity.ok(AppResponse.success("PDF uploaded and request fulfilled", response));

        } catch (IOException e) {
            log.error("Failed to upload PDF to Cloudinary: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(AppResponse.of(500, "Upload failed: " + e.getMessage(), null));
        } finally {
            // Clean up temp file
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
