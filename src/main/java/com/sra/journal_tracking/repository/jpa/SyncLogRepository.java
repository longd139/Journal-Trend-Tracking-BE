package com.sra.journal_tracking.repository.jpa;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.sra.journal_tracking.entity.jpa.SyncLog;

@Repository
public interface SyncLogRepository extends JpaRepository<SyncLog, UUID> {

    @EntityGraph(attributePaths = {"source"})
    @Query("""
            SELECT s FROM SyncLog s
            WHERE (:status IS NULL OR LOWER(s.status) = LOWER(:status))
              AND (:isManual IS NULL OR s.isManual = :isManual)
            """)
    Page<SyncLog> searchHistory(
            @Param("status") String status,
            @Param("isManual") Boolean isManual,
            Pageable pageable);
}
