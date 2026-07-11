package com.sra.journal_tracking.repository.jpa;

import com.sra.journal_tracking.entity.jpa.PaperRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaperRatingRepository extends JpaRepository<PaperRating, UUID> {

    /** Find a user's rating for a specific paper. */
    Optional<PaperRating> findByUser_UserIdAndPaper_PaperId(UUID userId, UUID paperId);

    /** Average rating score for a paper. Returns null if no ratings. */
    @Query("SELECT AVG(pr.score) FROM PaperRating pr WHERE pr.paper.paperId = :paperId")
    Double avgScoreByPaper_PaperId(@Param("paperId") UUID paperId);

    /** Count how many users rated a paper. */
    long countByPaper_PaperId(UUID paperId);
}
