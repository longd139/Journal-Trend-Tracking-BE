package com.sra.journal_tracking.service;

import com.sra.journal_tracking.dto.report.CreateReportRequest;
import com.sra.journal_tracking.dto.report.UpdateReportStatusRequest;
import com.sra.journal_tracking.dto.report.UserReportResponse;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface UserReportService {

    /** User tạo report mới. */
    UserReportResponse createReport(String email, CreateReportRequest request);

    /** User xem danh sách report của mình. */
    Page<UserReportResponse> getMyReports(String email, int page, int size);

    /** Admin xem tất cả report, lọc theo status. */
    Page<UserReportResponse> getAllReports(String status, int page, int size);

    /** Admin xem chi tiết 1 report. */
    UserReportResponse getReportDetail(UUID reportId);

    /** Admin cập nhật trạng thái report (reviewed/resolved/dismissed). */
    UserReportResponse updateReportStatus(UUID reportId, UpdateReportStatusRequest request, String adminEmail);
}
