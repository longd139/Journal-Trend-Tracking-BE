package com.sra.journal_tracking.repository.neo4j;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sra.journal_tracking.entity.jpa.Keyword;
import com.sra.journal_tracking.entity.jpa.ResearchPaper;

@Repository
public interface KeywordRepository extends JpaRepository<Keyword, UUID> {

    @Query("SELECT p FROM ResearchPaper p " +
           "JOIN p.keywords k " +
           "WHERE LOWER(k.keyword.keywordText) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<ResearchPaper> findPapersByKeywordText(@Param("keyword") String keyword, Pageable pageable);
}
