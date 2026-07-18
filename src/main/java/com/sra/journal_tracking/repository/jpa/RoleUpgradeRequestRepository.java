package com.sra.journal_tracking.repository.jpa;

import com.sra.journal_tracking.entity.jpa.RoleUpgradeRequest;
import com.sra.journal_tracking.entity.jpa.UpgradeRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface RoleUpgradeRequestRepository extends JpaRepository<RoleUpgradeRequest, UUID> {

    Page<RoleUpgradeRequest> findByUser_UserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<RoleUpgradeRequest> findByStatusOrderByCreatedAtDesc(UpgradeRequestStatus status, Pageable pageable);

    Page<RoleUpgradeRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByStatus(UpgradeRequestStatus status);
}
