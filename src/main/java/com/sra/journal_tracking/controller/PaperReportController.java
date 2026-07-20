package com.sra.journal_tracking.controller;

import com.sra.journal_tracking.dto.report.PaperReportRequestDTO;
import com.sra.journal_tracking.dto.report.PaperReportResponseDTO;
import com.sra.journal_tracking.dto.response.AppResponse;
import com.sra.journal_tracking.service.PaperReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/papers")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
public class PaperReportController {

    private final PaperReportService paperReportService;

    @Operation(
            summary = "Report/flag a paper",
            description = "Submit a report for a specific paper with reason, description, and optional images."
    )
    @PostMapping(value = "/{paperId}/reports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AppResponse<PaperReportResponseDTO>> reportPaper(
            @PathVariable UUID paperId,
            @Valid @RequestPart("data") PaperReportRequestDTO request,
            @RequestPart(value = "images", required = false) List<MultipartFile> images,
            Authentication authentication) {

        PaperReportResponseDTO response = paperReportService.submitReport(
                paperId, request, images, authentication.getName());
        return ResponseEntity.ok(AppResponse.success("Paper report submitted successfully", response));
    }
}
