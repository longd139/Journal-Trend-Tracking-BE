package com.sra.journal_tracking.repository.jpa;

import com.sra.journal_tracking.entity.jpa.IdeaAnalysis;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface IdeaAnalysisRepository extends JpaRepository<IdeaAnalysis, UUID> {

    Page<IdeaAnalysis> findByUser_UserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    long countByUser_UserId(UUID userId);

    void deleteByAnalysisIdAndUser_UserId(UUID analysisId, UUID userId);

    /** Count analyses for badge display on History tab */
    long countByUserUserId(UUID userId);
}
