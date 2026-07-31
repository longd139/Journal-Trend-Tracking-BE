package com.sra.journal_tracking.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sra.journal_tracking.dto.report.PaperReportRequestDTO;
import com.sra.journal_tracking.dto.report.PaperReportResponseDTO;
import com.sra.journal_tracking.entity.jpa.PaperReport;
import com.sra.journal_tracking.entity.jpa.ReportStatus;
import com.sra.journal_tracking.entity.jpa.ReportType;
import com.sra.journal_tracking.entity.jpa.User;
import com.sra.journal_tracking.entity.jpa.UserReport;
import com.sra.journal_tracking.exception.AppException;
import com.sra.journal_tracking.exception.ErrorCode;
import com.sra.journal_tracking.repository.jpa.PaperReportRepository;
import com.sra.journal_tracking.repository.jpa.UserReportRepository;
import com.sra.journal_tracking.repository.jpa.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaperReportService {

    private final PaperReportRepository paperReportRepository;
    private final UserRepository userRepository;
    private final UserReportRepository userReportRepository;
    private final AdminNotificationService adminNotificationService;
    private final Cloudinary cloudinary;
    private final ObjectMapper objectMapper;

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB per image
    private static final int MAX_IMAGES = 5;

    @Transactional(readOnly = true)
    public List<PaperReportResponseDTO> getReportsByPaperId(UUID paperId) {
        List<PaperReport> reports = paperReportRepository.findByPaperIdOrderByCreatedAtDesc(paperId);
        return reports.stream()
                .map(this::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public PaperReportResponseDTO submitReport(
            UUID paperId,
            PaperReportRequestDTO request,
            List<MultipartFile> images,
            String email) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        // Prevent duplicate reports
        if (paperReportRepository.existsByPaperIdAndUser_UserId(paperId, user.getUserId())) {
            throw new AppException(ErrorCode.REPORT_ALREADY_EXISTS);
        }

        // Upload images to Cloudinary
        List<String> imageUrls = new ArrayList<>();
        if (images != null && !images.isEmpty()) {
            if (images.size() > MAX_IMAGES) {
                throw new AppException(ErrorCode.REPORT_IMAGE_LIMIT);
            }
            for (MultipartFile file : images) {
                if (file.isEmpty()) continue;
                validateImage(file);
                try {
                    String url = uploadToCloudinary(file);
                    imageUrls.add(url);
                } catch (IOException e) {
                    log.error("Failed to upload report image: {}", e.getMessage());
                    throw new AppException(ErrorCode.REPORT_IMAGE_UPLOAD_FAILED);
                }
            }
        }

        // Serialize image URLs to JSON
        String imageUrlsJson = null;
        if (!imageUrls.isEmpty()) {
            try {
                imageUrlsJson = objectMapper.writeValueAsString(imageUrls);
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize image URLs", e);
                throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
            }
        }

        PaperReport report = PaperReport.builder()
                .paperId(paperId)
                .user(user)
                .reason(request.getReason())
                .description(request.getDescription())
                .imageUrls(imageUrlsJson)
                .status("PENDING")
                .build();

        PaperReport saved = paperReportRepository.save(report);
        log.info("Paper report submitted: reportId={}, paperId={}, userId={}, reason={}",
                saved.getReportId(), paperId, user.getUserId(), request.getReason());

        // Bridge to USER_REPORT so it appears in the admin reports panel
        bridgeToUserReport(user, paperId, request);

        return toResponseDTO(saved);
    }

    /**
     * Create a corresponding USER_REPORT entry so the admin panel
     * at /admin/reports can see paper flagging reports alongside
     * other report types.
     */
    private void bridgeToUserReport(User user, UUID paperId, PaperReportRequestDTO request) {
        try {
            UserReport userReport = UserReport.builder()
                    .user(user)
                    .reportType(ReportType.PAPER_FLAG)
                    .targetType("paper")
                    .targetId(paperId)
                    .title("Paper flagged: " + request.getReason())
                    .description(request.getDescription())
                    .status(ReportStatus.PENDING)
                    .build();
            userReportRepository.save(userReport);
            log.info("Bridged PAPER_REPORT -> USER_REPORT for paperId={}", paperId);

            // Notify admins
            adminNotificationService.broadcastToAdmins(
                    com.sra.journal_tracking.entity.jpa.NotificationType.USER_REPORT,
                    "Paper Flagged — " + request.getReason(),
                    "User " + user.getFullName() + " (" + user.getEmail() + ") flagged paper "
                            + paperId + " for: " + request.getReason()
            );
        } catch (Exception e) {
            log.warn("Failed to bridge paper report to USER_REPORT: {}", e.getMessage());
        }
    }

    private void validateImage(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new AppException(ErrorCode.REPORT_IMAGE_INVALID);
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new AppException(ErrorCode.REPORT_IMAGE_TOO_LARGE);
        }
    }

    private String uploadToCloudinary(MultipartFile file) throws IOException {
        @SuppressWarnings("unchecked")
        Map<String, Object> result = cloudinary.uploader().upload(
                file.getBytes(),
                ObjectUtils.asMap(
                        "folder", "scitrack/reports",
                        "resource_type", "image"
                )
        );
        return (String) result.get("secure_url");
    }

    private PaperReportResponseDTO toResponseDTO(PaperReport report) {
        List<String> urls = parseImageUrls(report.getImageUrls());
        return PaperReportResponseDTO.builder()
                .reportId(report.getReportId())
                .paperId(report.getPaperId())
                .reason(report.getReason())
                .description(report.getDescription())
                .imageUrls(urls)
                .status(report.getStatus())
                .createdAt(report.getCreatedAt())
                .build();
    }

    private List<String> parseImageUrls(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse imageUrls JSON: {}", json);
            return List.of();
        }
    }
}
