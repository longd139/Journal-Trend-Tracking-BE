package com.sra.journal_tracking.controller;

import com.sra.journal_tracking.dto.pdf.PdfCandidateSearchResponse;
import com.sra.journal_tracking.dto.pdf.PdfRequestFulfillRequest;
import com.sra.journal_tracking.dto.pdf.PdfRequestRejectRequest;
import com.sra.journal_tracking.dto.pdf.PdfRequestResponse;
import com.sra.journal_tracking.dto.response.AppResponse;
import com.sra.journal_tracking.service.PdfRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/pdf-requests")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminPdfRequestController {

    private final PdfRequestService pdfRequestService;

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
