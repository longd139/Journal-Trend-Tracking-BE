package com.sra.journal_tracking.repository.jpa;

import com.sra.journal_tracking.entity.jpa.PaperEvaluationCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaperEvalCacheRepository extends JpaRepository<PaperEvaluationCache, UUID> {

    /**
     * Find cached paper evaluation by paper ID + idea hash.
     * The unique constraint on (paper_id, idea_hash) guarantees at most 1 row.
     */
    Optional<PaperEvaluationCache> findByPaperIdAndIdeaHash(UUID paperId, String ideaHash);

    /** Delete cache entries older than the given cutoff timestamp. */
    void deleteByCreatedAtBefore(LocalDateTime cutoff);
}
