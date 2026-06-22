package com.sra.journal_tracking.repository.jpa;

import com.sra.journal_tracking.entity.jpa.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    @EntityGraph(attributePaths = {"admin", "admin.role"})
    @Query("""
            SELECT a FROM AuditLog a
            WHERE (:action IS NULL OR LOWER(a.action) LIKE LOWER(CONCAT('%', :action, '%')))
              AND (:adminId IS NULL OR a.admin.userId = :adminId)
            """)
    Page<AuditLog> searchAuditLogs(
            @Param("action") String action,
            @Param("adminId") UUID adminId,
            Pageable pageable);
}
