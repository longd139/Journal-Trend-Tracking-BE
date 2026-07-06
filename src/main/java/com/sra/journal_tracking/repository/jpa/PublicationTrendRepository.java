package com.sra.journal_tracking.repository.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.sra.journal_tracking.entity.jpa.PublicationTrend;

@Repository
public interface PublicationTrendRepository extends JpaRepository<PublicationTrend, UUID> {

    /**
     * Find the publication trend entry for a topic with the highest GrowthRate.
     * Returns the trend entry with the largest non-null GrowthRate where TrendTarget = 'topic'.
     */
    @Query("SELECT pt FROM PublicationTrend pt " +
           "WHERE pt.trendTarget = 'topic' " +
           "  AND pt.growthRate IS NOT NULL " +
           "ORDER BY pt.growthRate DESC")
    Optional<PublicationTrend> findTopGrowthTopic();

    /**
     * Find trends of a specific period type, ordered by calculated date descending (newest first).
     * Used for the publication trend timeline endpoint.
     */
    List<PublicationTrend> findByPeriodTypeOrderByCalculatedAtDesc(String periodType, Pageable pageable);

    /**
     * Find all trends for a specific target (topic, field, or journal),
     * ordered by calculated date ascending (chronological).
     */
    @Query("SELECT pt FROM PublicationTrend pt " +
           "WHERE pt.trendTarget = :trendTarget AND pt.targetId = :targetId " +
           "ORDER BY pt.calculatedAt ASC")
    List<PublicationTrend> findByTrendTargetAndTargetId(@org.springframework.data.repository.query.Param("trendTarget") String trendTarget,
                                                         @org.springframework.data.repository.query.Param("targetId") UUID targetId);
}
