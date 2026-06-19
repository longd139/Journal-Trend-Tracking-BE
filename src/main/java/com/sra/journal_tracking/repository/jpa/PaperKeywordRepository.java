package com.sra.journal_tracking.repository.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.sra.journal_tracking.entity.jpa.PaperKeyword;
import com.sra.journal_tracking.entity.jpa.PaperKeywordId;

import org.springframework.data.domain.Pageable;

import java.util.List;

@Repository
public interface PaperKeywordRepository extends JpaRepository<PaperKeyword, PaperKeywordId> {
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM PaperKeyword pk " +
           "WHERE pk.keyword.normalizedText = :normalizedText " +
           "AND pk.relevanceScore = 1.0")
    int deleteSyntheticKeywordLinks(@Param("normalizedText") String normalizedText);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM PaperKeyword pk " +
           "WHERE pk.keyword.normalizedText LIKE LOWER(CONCAT('%', :token, '%')) " +
           "AND pk.relevanceScore = 1.0")
    int deleteSyntheticKeywordLinksContainingToken(@Param("token") String token);

    @Query("SELECT pk2.keyword.keywordText " +
           "FROM PaperKeyword pk1, PaperKeyword pk2 " +
           "WHERE pk1.paper = pk2.paper " +
           "AND pk1.keyword.normalizedText IN :normalizedTerms " +
           "AND pk2.keyword.normalizedText NOT IN :normalizedTerms " +
           "AND pk2.relevanceScore >= :minScore " +
           "GROUP BY pk2.keyword.keywordText " +
           "ORDER BY COUNT(pk2.keyword.keywordId) DESC")
    List<String> findRelatedKeywordTexts(
            @Param("normalizedTerms") List<String> normalizedTerms,
            @Param("minScore") Double minScore,
            Pageable pageable);
}
