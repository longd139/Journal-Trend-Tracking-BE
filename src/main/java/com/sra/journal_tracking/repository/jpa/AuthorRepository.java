package com.sra.journal_tracking.repository.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.sra.journal_tracking.entity.jpa.Author;

@Repository
public interface AuthorRepository extends JpaRepository<Author, UUID> {
    Optional<Author> findByExternalAuthorIdAndSource_SourceId(String externalAuthorId, UUID sourceId);

    Optional<Author> findByFullNameAndSource_SourceId(String fullName, UUID sourceId);

    /** Top authors by total citations (for suggested authors zero-state). */
    List<Author> findAllByOrderByTotalCitationsDesc(Pageable pageable);

    /** Top authors by paper count in our DB (authors with most PAPER_AUTHOR entries). */
    @Query("SELECT a FROM Author a "
         + "WHERE a.externalAuthorId IS NOT NULL AND a.externalAuthorId <> '' "
         + "ORDER BY (SELECT COUNT(pa) FROM PaperAuthor pa WHERE pa.author = a) DESC")
    List<Author> findTopAuthorsByPaperCount(Pageable pageable);

    /** Fuzzy search author by name (case-insensitive LIKE). */
    @Query("SELECT a FROM Author a WHERE LOWER(a.fullName) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<Author> searchByName(@Param("name") String name, Pageable pageable);

    /** Find first author matching the given full name (exact match, case-sensitive). */
    Optional<Author> findFirstByFullName(String fullName);
}
