package com.sra.journal_tracking.repository.jpa;

import com.sra.journal_tracking.entity.jpa.PdfRequest;
import com.sra.journal_tracking.entity.jpa.PdfRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PdfRequestRepository extends JpaRepository<PdfRequest, UUID> {

    Optional<PdfRequest> findByUser_UserIdAndPaper_PaperIdAndStatus(
            UUID userId,
            UUID paperId,
            PdfRequestStatus status);

    @EntityGraph(attributePaths = {"user", "paper", "paper.journal", "resolvedByAdmin"})
    Page<PdfRequest> findAllByOrderByRequestedAtDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"user", "paper", "paper.journal", "resolvedByAdmin"})
    Page<PdfRequest> findByStatusOrderByRequestedAtDesc(PdfRequestStatus status, Pageable pageable);
}
