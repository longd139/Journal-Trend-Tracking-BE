package com.sra.journal_tracking.service.impl;

import com.sra.journal_tracking.dto.report.CreateReportRequest;
import com.sra.journal_tracking.dto.report.UpdateReportStatusRequest;
import com.sra.journal_tracking.dto.report.UserReportResponse;
import com.sra.journal_tracking.entity.jpa.NotificationType;
import com.sra.journal_tracking.entity.jpa.ReportStatus;
import com.sra.journal_tracking.entity.jpa.ReportType;
import com.sra.journal_tracking.entity.jpa.User;
import com.sra.journal_tracking.entity.jpa.UserReport;
import com.sra.journal_tracking.exception.AppException;
import com.sra.journal_tracking.exception.ErrorCode;
import com.sra.journal_tracking.repository.jpa.UserReportRepository;
import com.sra.journal_tracking.repository.jpa.UserRepository;
import com.sra.journal_tracking.service.AdminNotificationService;
import com.sra.journal_tracking.service.UserReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserReportServiceImpl implements UserReportService {

    private final UserReportRepository userReportRepository;
    private final UserRepository userRepository;
    private final AdminNotificationService adminNotificationService;

    @Override
    @Transactional
    public UserReportResponse createReport(String email, CreateReportRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        ReportType reportType;
        try {
            reportType = ReportType.valueOf(request.getReportType().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }

        UserReport report = UserReport.builder()
                .user(user)
                .reportType(reportType)
                .targetType(request.getTargetType())
                .targetId(request.getTargetId())
                .title(request.getTitle())
                .description(request.getDescription())
                .status(ReportStatus.PENDING)
                .build();

        report = userReportRepository.save(report);
        log.info("User report created: id={}, type={}, user={}", report.getReportId(), reportType, email);

        // Notify admins
        try {
            adminNotificationService.broadcastToAdmins(
                    NotificationType.USER_REPORT,
                    "New User Report — " + request.getReportType(),
                    "User " + user.getFullName() + " (" + email + ") submitted a report: \""
                            + request.getTitle() + "\""
            );
        } catch (Exception e) {
            log.warn("Failed to broadcast USER_REPORT admin notification: {}", e.getMessage());
        }

        return mapToResponse(report);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserReportResponse> getMyReports(String email, int page, int size) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return userReportRepository.findByUser_UserIdOrderByCreatedAtDesc(user.getUserId(), pageRequest)
                .map(this::mapToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserReportResponse> getAllReports(String status, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        if (status != null && !status.isBlank()) {
            try {
                ReportStatus reportStatus = ReportStatus.valueOf(status.toUpperCase());
                return userReportRepository.findByStatusOrderByCreatedAtDesc(reportStatus, pageRequest)
                        .map(this::mapToResponse);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid report status filter: {}", status);
                return Page.empty();
            }
        }

        return userReportRepository.findAllByOrderByCreatedAtDesc(pageRequest)
                .map(this::mapToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public UserReportResponse getReportDetail(UUID reportId) {
        UserReport report = userReportRepository.findById(reportId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));
        return mapToResponse(report);
    }

    @Override
    @Transactional
    public UserReportResponse updateReportStatus(UUID reportId, UpdateReportStatusRequest request, String adminEmail) {
        UserReport report = userReportRepository.findById(reportId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));

        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        ReportStatus newStatus;
        try {
            newStatus = ReportStatus.valueOf(request.getStatus().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }

        report.setStatus(newStatus);
        report.setAdminNote(request.getAdminNote());

        if (newStatus == ReportStatus.RESOLVED || newStatus == ReportStatus.DISMISSED) {
            report.setResolvedByAdmin(admin);
            report.setResolvedAt(LocalDateTime.now());
        }

        report = userReportRepository.save(report);
        log.info("Report {} status updated to {} by admin {}", reportId, newStatus, adminEmail);

        return mapToResponse(report);
    }

    // ── Private mapper ──

    private UserReportResponse mapToResponse(UserReport r) {
        return UserReportResponse.builder()
                .reportId(r.getReportId())
                .userId(r.getUser() != null ? r.getUser().getUserId() : null)
                .userEmail(r.getUser() != null ? r.getUser().getEmail() : null)
                .userFullName(r.getUser() != null ? r.getUser().getFullName() : null)
                .reportType(r.getReportType() != null ? r.getReportType().name().toLowerCase() : null)
                .targetType(r.getTargetType())
                .targetId(r.getTargetId())
                .title(r.getTitle())
                .description(r.getDescription())
                .status(r.getStatus() != null ? r.getStatus().name().toLowerCase() : null)
                .adminNote(r.getAdminNote())
                .resolvedByAdminId(r.getResolvedByAdmin() != null ? r.getResolvedByAdmin().getUserId() : null)
                .resolvedByAdminName(r.getResolvedByAdmin() != null ? r.getResolvedByAdmin().getFullName() : null)
                .createdAt(r.getCreatedAt())
                .resolvedAt(r.getResolvedAt())
                .build();
    }
}
