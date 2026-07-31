package com.sra.journal_tracking.repository.jpa;

import com.sra.journal_tracking.entity.jpa.ReportStatus;
import com.sra.journal_tracking.entity.jpa.UserReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface UserReportRepository extends JpaRepository<UserReport, UUID> {

    /** Lấy tất cả report, lọc theo status (null = tất cả), mới nhất trước. */
    Page<UserReport> findByStatusOrderByCreatedAtDesc(ReportStatus status, Pageable pageable);

    /** Lấy tất cả report không lọc status, mới nhất trước. */
    Page<UserReport> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** Lấy report của 1 user, mới nhất trước. */
    Page<UserReport> findByUser_UserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /** Đếm số report theo status (cho admin dashboard badge). */
    long countByStatus(ReportStatus status);
}
