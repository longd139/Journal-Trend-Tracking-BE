package com.sra.journal_tracking.controller;

import com.sra.journal_tracking.dto.report.UpdateReportStatusRequest;
import com.sra.journal_tracking.dto.report.UserReportResponse;
import com.sra.journal_tracking.dto.response.AppResponse;
import com.sra.journal_tracking.service.UserReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/admin/reports")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "Bearer Authentication")
public class AdminReportController {

    private final UserReportService userReportService;

    @Operation(
            summary = "Lấy tất cả báo cáo (Admin)",
            description = "Admin xem danh sách tất cả user reports, có thể lọc theo status."
    )
    @GetMapping
    public ResponseEntity<AppResponse<Page<UserReportResponse>>> getAllReports(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (page < 0) page = 0;
        if (size < 1) size = 1;
        if (size > 50) size = 50;

        Page<UserReportResponse> reports = userReportService.getAllReports(status, page, size);
        return ResponseEntity.ok(AppResponse.success("Reports retrieved", reports));
    }

    @Operation(
            summary = "Xem chi tiết báo cáo (Admin)",
            description = "Admin xem chi tiết 1 user report."
    )
    @GetMapping("/{id}")
    public ResponseEntity<AppResponse<UserReportResponse>> getReportDetail(
            @PathVariable UUID id) {

        UserReportResponse report = userReportService.getReportDetail(id);
        return ResponseEntity.ok(AppResponse.success("Report detail retrieved", report));
    }

    @Operation(
            summary = "Cập nhật trạng thái báo cáo (Admin)",
            description = "Admin cập nhật trạng thái report (reviewed/resolved/dismissed) kèm ghi chú."
    )
    @PutMapping("/{id}/status")
    public ResponseEntity<AppResponse<UserReportResponse>> updateReportStatus(
            @PathVariable UUID id,
            Authentication authentication,
            @Valid @RequestBody UpdateReportStatusRequest request) {

        UserReportResponse report = userReportService.updateReportStatus(
                id, request, authentication.getName());
        return ResponseEntity.ok(AppResponse.success("Report status updated", report));
    }
}
