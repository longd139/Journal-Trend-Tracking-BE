package com.sra.journal_tracking.repository.jpa;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.sra.journal_tracking.entity.jpa.ResearchTopic;

@Repository
public interface ResearchTopicRepository extends JpaRepository<ResearchTopic, UUID> {

    /**
     * Count topics currently marked as trending.
     */
    long countByIsTrendingTrue();

    @Query("SELECT COUNT(t) FROM ResearchTopic t WHERE t.isTrending = true AND t.updatedAt >= :start AND t.updatedAt < :end")
    long countTrendingByUpdatedAtBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    Optional<ResearchTopic> findTopByIsTrendingTrueOrderByTrendScoreDesc();

    /**
     * Paginated list of trending topics, ordered by trend score descending (hottest first).
     */
    List<ResearchTopic> findByIsTrendingTrueOrderByTrendScoreDesc(Pageable pageable);

    /**
     * Topics within a specific research field, ordered by trend score descending.
     */
    List<ResearchTopic> findByField_FieldIdOrderByTrendScoreDesc(UUID fieldId, Pageable pageable);
}
