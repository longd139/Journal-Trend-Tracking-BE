package com.sra.journal_tracking.service;

import com.sra.journal_tracking.dto.upgrade.ApproveRejectRequest;
import com.sra.journal_tracking.dto.upgrade.CreateUpgradeRequest;
import com.sra.journal_tracking.dto.upgrade.UpgradeRequestDTO;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface UpgradeRequestService {

    UpgradeRequestDTO submitRequest(String email, CreateUpgradeRequest request);

    Page<UpgradeRequestDTO> getUserRequests(String email, int page, int size);

    UpgradeRequestDTO getUserRequestDetail(String email, UUID requestId);

    Page<UpgradeRequestDTO> getAdminRequests(String status, int page, int size);

    UpgradeRequestDTO getAdminRequestDetail(UUID requestId);

    UpgradeRequestDTO approveRequest(UUID requestId, String adminEmail, ApproveRejectRequest request);

    UpgradeRequestDTO rejectRequest(UUID requestId, String adminEmail, ApproveRejectRequest request);
}
