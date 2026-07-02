package com.sra.journal_tracking.controller;

import com.sra.journal_tracking.dto.pdf.PdfRequestCreateRequest;
import com.sra.journal_tracking.dto.pdf.PdfRequestResponse;
import com.sra.journal_tracking.dto.response.AppResponse;
import com.sra.journal_tracking.service.PdfRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/papers")
@RequiredArgsConstructor
public class PdfRequestController {

    private final PdfRequestService pdfRequestService;

    @PostMapping("/{paperId}/pdf-requests")
    public ResponseEntity<AppResponse<PdfRequestResponse>> requestPdf(
            @PathVariable UUID paperId,
            Authentication authentication,
            @Valid @RequestBody(required = false) PdfRequestCreateRequest request) {
        return ResponseEntity.ok(AppResponse.success(
                "PDF request submitted",
                pdfRequestService.requestPdf(paperId, authentication.getName(), request)));
    }
}
