package com.sra.journal_tracking.repository.jpa;

import com.sra.journal_tracking.entity.jpa.PaperReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PaperReportRepository extends JpaRepository<PaperReport, UUID> {

    List<PaperReport> findByPaperIdOrderByCreatedAtDesc(UUID paperId);

    List<PaperReport> findByUser_UserIdOrderByCreatedAtDesc(UUID userId);

    boolean existsByPaperIdAndUser_UserId(UUID paperId, UUID userId);
}
