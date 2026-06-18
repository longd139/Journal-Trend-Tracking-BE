package com.sra.journal_tracking.repository.jpa;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.sra.journal_tracking.entity.jpa.ResearchPaper;

@Repository
public interface ResearchPaperRepository extends JpaRepository<ResearchPaper, UUID> {

    @Query("SELECT DISTINCT p FROM ResearchPaper p " +
           "LEFT JOIN FETCH p.keywords k " +
           "LEFT JOIN FETCH k.keyword kw " +
           "WHERE LOWER(p.title) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "   OR LOWER(p.abstractText) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "   OR LOWER(kw.keywordText) LIKE LOWER(CONCAT('%', :query, '%'))")
    Page<ResearchPaper> searchByTitleOrAbstractOrKeywords(@Param("query") String query, Pageable pageable);

    // ── Phase 1a: Keyword-only search (chỉ Title + Abstract, 0 JOIN — siêu nhanh) ──
    // Có cache: search lại keyword cũ → trả về ngay lập tức
    @Cacheable(value = "paperSearchIds", key = "#query + '_' + #pageable.pageNumber + '_' + #pageable.pageSize")
    @Query("SELECT p.paperId FROM ResearchPaper p " +
           "WHERE LOWER(p.title) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "   OR LOWER(p.abstractText) LIKE LOWER(CONCAT('%', :query, '%'))")
    Page<UUID> searchPaperIdsByKeyword(@Param("query") String query, Pageable pageable);

    // ── Phase 1b: Full search với filter (có JOIN — dùng khi có author/journal filter) ──
    // Dùng subquery để tách DISTINCT khỏi ORDER BY (SQL Server yêu cầu ORDER BY column
    // phải có trong SELECT khi dùng DISTINCT)
    @Query(value = "SELECT p.paperId FROM ResearchPaper p " +
           "WHERE p.paperId IN (" +
           "  SELECT DISTINCT p2.paperId FROM ResearchPaper p2 " +
           "  LEFT JOIN p2.keywords k " +
           "  LEFT JOIN k.keyword kw " +
           "  LEFT JOIN p2.authors pa " +
           "  LEFT JOIN pa.author a " +
           "  LEFT JOIN p2.journal j " +
           "  WHERE (LOWER(p2.title) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "     OR LOWER(p2.abstractText) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "     OR LOWER(p2.doi) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "     OR LOWER(kw.keywordText) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "     OR LOWER(a.fullName) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "     OR LOWER(j.journalName) LIKE LOWER(CONCAT('%', :query, '%'))) " +
           "    AND (:authorName IS NULL OR :authorName = '' OR LOWER(a.fullName) LIKE LOWER(CONCAT('%', :authorName, '%'))) " +
           "    AND (:journalId IS NULL OR p2.journal.journalId = :journalId) " +
           ") ",
           countQuery = "SELECT COUNT(DISTINCT p2.paperId) FROM ResearchPaper p2 " +
           "LEFT JOIN p2.keywords k " +
           "LEFT JOIN k.keyword kw " +
           "LEFT JOIN p2.authors pa " +
           "LEFT JOIN pa.author a " +
           "LEFT JOIN p2.journal j " +
           "WHERE (LOWER(p2.title) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "   OR LOWER(p2.abstractText) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "   OR LOWER(p2.doi) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "   OR LOWER(kw.keywordText) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "   OR LOWER(a.fullName) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "   OR LOWER(j.journalName) LIKE LOWER(CONCAT('%', :query, '%'))) " +
           "  AND (:authorName IS NULL OR :authorName = '' OR LOWER(a.fullName) LIKE LOWER(CONCAT('%', :authorName, '%'))) " +
           "  AND (:journalId IS NULL OR p2.journal.journalId = :journalId)")
    Page<UUID> searchPaperIdsWithFilters(
            @Param("query") String query,
            @Param("authorName") String authorName,
            @Param("journalId") UUID journalId,
            Pageable pageable
    );

    // ── Phase 2: Load chi tiết paper theo danh sách ID (có JOIN FETCH, chỉ ~20 papers) ──
    @Query("SELECT DISTINCT p FROM ResearchPaper p " +
           "LEFT JOIN FETCH p.keywords k " +
           "LEFT JOIN FETCH k.keyword kw " +
           "LEFT JOIN FETCH p.authors pa " +
           "LEFT JOIN FETCH pa.author a " +
           "LEFT JOIN FETCH p.journal j " +
           "WHERE p.paperId IN :ids")
    List<ResearchPaper> findByIdsWithDetails(@Param("ids") List<UUID> ids);

    // ── ID-only queries (nhanh, SQL phân trang thực sự) ──

    @Query("SELECT DISTINCT pa.paper.paperId FROM PaperAuthor pa " +
           "WHERE LOWER(pa.author.fullName) LIKE LOWER(CONCAT('%', :authorName, '%'))")
    Page<UUID> searchAuthorPaperIds(@Param("authorName") String authorName, Pageable pageable);

    @Query("SELECT p.paperId FROM ResearchPaper p WHERE p.journal.journalId = :journalId")
    Page<UUID> findPaperIdsByJournalId(@Param("journalId") UUID journalId, Pageable pageable);

    @Query("SELECT p.paperId FROM ResearchPaper p " +
           "WHERE (:pubYearFrom IS NULL OR p.pubYear >= :pubYearFrom) " +
           "  AND (:pubYearTo IS NULL OR p.pubYear <= :pubYearTo) " +
           "  AND (:fieldId IS NULL OR p.field.fieldId = :fieldId) " +
           "  AND (:journalId IS NULL OR p.journal.journalId = :journalId) " +
           "  AND (:isOpenAccess IS NULL OR p.isOpenAccess = :isOpenAccess) " +
           "  AND (:minCitations IS NULL OR p.citationCount >= :minCitations)")
    Page<UUID> advancedFilterIds(
            @Param("pubYearFrom") Short pubYearFrom,
            @Param("pubYearTo") Short pubYearTo,
            @Param("fieldId") UUID fieldId,
            @Param("journalId") UUID journalId,
            @Param("isOpenAccess") Boolean isOpenAccess,
            @Param("minCitations") Integer minCitations,
            Pageable pageable
    );

    @Query("SELECT p FROM ResearchPaper p " +
           "LEFT JOIN FETCH p.journal " +
           "LEFT JOIN FETCH p.field " +
           "LEFT JOIN FETCH p.authors pa " +
           "LEFT JOIN FETCH pa.author " +
           "WHERE p.paperId = :paperId")
    Optional<ResearchPaper> findByIdWithDetails(@Param("paperId") UUID paperId);

    Optional<ResearchPaper> findByDoi(String doi);

    // ---- Overview Statistics Queries ----

    @Query("SELECT COUNT(p) FROM ResearchPaper p WHERE p.createdAt >= :start AND p.createdAt < :end")
    long countByCreatedAtBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COALESCE(SUM(p.citationCount), 0) FROM ResearchPaper p")
    long sumAllCitationCounts();

    @Query("SELECT COALESCE(SUM(p.citationCount), 0) FROM ResearchPaper p WHERE p.createdAt >= :start AND p.createdAt < :end")
    long sumCitationCountsByCreatedAtBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
