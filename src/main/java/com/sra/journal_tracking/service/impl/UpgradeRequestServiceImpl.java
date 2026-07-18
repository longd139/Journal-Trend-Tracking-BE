package com.sra.journal_tracking.service.impl;

import com.sra.journal_tracking.dto.upgrade.ApproveRejectRequest;
import com.sra.journal_tracking.dto.upgrade.CreateUpgradeRequest;
import com.sra.journal_tracking.dto.upgrade.UpgradeRequestDTO;
import com.sra.journal_tracking.entity.jpa.*;
import com.sra.journal_tracking.exception.AppException;
import com.sra.journal_tracking.exception.ErrorCode;
import com.sra.journal_tracking.repository.jpa.NotificationRepository;
import com.sra.journal_tracking.repository.jpa.RoleRepository;
import com.sra.journal_tracking.repository.jpa.RoleUpgradeRequestRepository;
import com.sra.journal_tracking.repository.jpa.UserRepository;
import com.sra.journal_tracking.service.NotificationEventPublisher;
import com.sra.journal_tracking.service.UpgradeRequestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpgradeRequestServiceImpl implements UpgradeRequestService {

    private final RoleUpgradeRequestRepository repository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationEventPublisher eventPublisher;

    @Override
    @Transactional
    public UpgradeRequestDTO submitRequest(String email, CreateUpgradeRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        RoleUpgradeRequest entity = RoleUpgradeRequest.builder()
                .user(user)
                .fullName(request.getFullName())
                .institution(request.getInstitution())
                .researchField(request.getResearchField())
                .position(request.getPosition())
                .orcid(request.getOrcid())
                .reason(request.getReason())
                .status(UpgradeRequestStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        entity = repository.save(entity);
        log.info("Upgrade request submitted by user {}: {}", email, entity.getRequestId());

        // Notify all admins
        notifyAdminsOfNewRequest(user, entity);

        return mapToDTO(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UpgradeRequestDTO> getUserRequests(String email, int page, int size) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        return repository.findByUser_UserIdOrderByCreatedAtDesc(user.getUserId(), PageRequest.of(page, size))
                .map(this::mapToDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public UpgradeRequestDTO getUserRequestDetail(String email, UUID requestId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        RoleUpgradeRequest request = repository.findById(requestId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));

        if (!request.getUser().getUserId().equals(user.getUserId())) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        return mapToDTO(request);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UpgradeRequestDTO> getAdminRequests(String status, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);

        if (status != null && !status.isBlank()) {
            UpgradeRequestStatus reqStatus;
            try {
                reqStatus = UpgradeRequestStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
            }
            return repository.findByStatusOrderByCreatedAtDesc(reqStatus, pageRequest)
                    .map(this::mapToDTO);
        }

        return repository.findAllByOrderByCreatedAtDesc(pageRequest)
                .map(this::mapToDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public UpgradeRequestDTO getAdminRequestDetail(UUID requestId) {
        return repository.findById(requestId)
                .map(this::mapToDTO)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Override
    @Transactional
    public UpgradeRequestDTO approveRequest(UUID requestId, String adminEmail, ApproveRejectRequest req) {
        RoleUpgradeRequest upgradeReq = repository.findById(requestId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));

        if (upgradeReq.getStatus() != UpgradeRequestStatus.PENDING) {
            throw new RuntimeException("Request is already " + upgradeReq.getStatus().name().toLowerCase());
        }

        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        // Update request status
        upgradeReq.setStatus(UpgradeRequestStatus.APPROVED);
        upgradeReq.setReviewedBy(admin);
        upgradeReq.setReviewedAt(LocalDateTime.now());
        if (req.getAdminNote() != null) {
            upgradeReq.setAdminNote(req.getAdminNote());
        }
        repository.save(upgradeReq);

        // Upgrade user role to researcher
        User targetUser = upgradeReq.getUser();
        Role researcherRole = roleRepository.findByRoleNameIgnoreCase("researcher")
                .orElseThrow(() -> new RuntimeException("Role researcher not found"));
        targetUser.setRole(researcherRole);
        userRepository.save(targetUser);

        log.info("Upgrade request {} approved by admin {} for user {}", requestId, admin.getEmail(), targetUser.getEmail());

        // Notify user
        notifyUser(upgradeReq, "Your upgrade request has been approved!",
                "Congratulations! Your request to upgrade to Researcher has been approved. You now have unlimited access to all features.");

        return mapToDTO(upgradeReq);
    }

    @Override
    @Transactional
    public UpgradeRequestDTO rejectRequest(UUID requestId, String adminEmail, ApproveRejectRequest req) {
        RoleUpgradeRequest upgradeReq = repository.findById(requestId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));

        if (upgradeReq.getStatus() != UpgradeRequestStatus.PENDING) {
            throw new RuntimeException("Request is already " + upgradeReq.getStatus().name().toLowerCase());
        }

        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        upgradeReq.setStatus(UpgradeRequestStatus.REJECTED);
        upgradeReq.setReviewedBy(admin);
        upgradeReq.setReviewedAt(LocalDateTime.now());
        upgradeReq.setAdminNote(req.getAdminNote());
        repository.save(upgradeReq);

        log.info("Upgrade request {} rejected by admin {} for user {}", requestId, admin.getEmail(), upgradeReq.getUser().getEmail());

        // Notify user
        String reason = req.getAdminNote() != null ? " Reason: " + req.getAdminNote() : "";
        notifyUser(upgradeReq, "Your upgrade request was rejected",
                "Your request to upgrade to Researcher has been rejected." + reason);

        return mapToDTO(upgradeReq);
    }

    private void notifyAdminsOfNewRequest(User requester, RoleUpgradeRequest request) {
        java.util.List<User> admins = userRepository.findAllByIsActiveTrue();
        // We need to get admin-role users; the query already has isActive filter
        // but we need to filter by role. Let's do a simple approach:
        // Send notification to all admins via the notification table
        for (User admin : admins) {
            if ("admin".equalsIgnoreCase(admin.getRole().getRoleName())) {
                Notification notif = Notification.builder()
                        .user(admin)
                        .type(NotificationType.SYSTEM)
                        .title("New upgrade request from " + requester.getFullName())
                        .message(requester.getFullName() + " from " + request.getInstitution()
                                + " (" + request.getResearchField() + ") has requested an upgrade to Researcher.")
                        .isRead(false)
                        .createdAt(LocalDateTime.now())
                        .build();
                notif = notificationRepository.save(notif);
                eventPublisher.publish(admin.getUserId(), notif);
            }
        }
    }

    private void notifyUser(RoleUpgradeRequest request, String title, String message) {
        Notification notif = Notification.builder()
                .user(request.getUser())
                .type(NotificationType.SYSTEM)
                .title(title)
                .message(message)
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .build();
        notif = notificationRepository.save(notif);
        eventPublisher.publish(request.getUser().getUserId(), notif);
    }

    private UpgradeRequestDTO mapToDTO(RoleUpgradeRequest entity) {
        return UpgradeRequestDTO.builder()
                .requestId(entity.getRequestId())
                .userId(entity.getUser().getUserId())
                .userEmail(entity.getUser().getEmail())
                .userFullName(entity.getFullName())
                .institution(entity.getInstitution())
                .researchField(entity.getResearchField())
                .position(entity.getPosition())
                .orcid(entity.getOrcid())
                .reason(entity.getReason())
                .status(entity.getStatus().name())
                .adminNote(entity.getAdminNote())
                .reviewedBy(entity.getReviewedBy() != null ? entity.getReviewedBy().getUserId() : null)
                .reviewedByName(entity.getReviewedBy() != null ? entity.getReviewedBy().getFullName() : null)
                .reviewedAt(entity.getReviewedAt())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
