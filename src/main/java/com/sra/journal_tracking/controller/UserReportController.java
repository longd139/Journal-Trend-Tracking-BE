package com.sra.journal_tracking.controller;

import com.sra.journal_tracking.dto.report.CreateReportRequest;
import com.sra.journal_tracking.dto.report.UserReportResponse;
import com.sra.journal_tracking.dto.response.AppResponse;
import com.sra.journal_tracking.service.UserReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
public class UserReportController {

    private final UserReportService userReportService;

    @Operation(
            summary = "Tạo báo cáo mới",
            description = "Người dùng gửi báo cáo về vấn đề PDF, nội dung bài báo, hoặc vấn đề khác."
    )
    @PostMapping
    public ResponseEntity<AppResponse<UserReportResponse>> createReport(
            Authentication authentication,
            @Valid @RequestBody CreateReportRequest request) {

        UserReportResponse report = userReportService.createReport(authentication.getName(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AppResponse.success("Report submitted successfully", report));
    }

    @Operation(
            summary = "Xem báo cáo của tôi",
            description = "Người dùng xem danh sách báo cáo mình đã gửi."
    )
    @GetMapping("/my")
    public ResponseEntity<AppResponse<Page<UserReportResponse>>> getMyReports(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (page < 0) page = 0;
        if (size < 1) size = 1;
        if (size > 50) size = 50;

        Page<UserReportResponse> reports = userReportService.getMyReports(authentication.getName(), page, size);
        return ResponseEntity.ok(AppResponse.success("Reports retrieved", reports));
    }
}
