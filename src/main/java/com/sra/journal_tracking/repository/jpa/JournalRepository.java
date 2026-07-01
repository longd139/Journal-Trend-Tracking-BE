package com.sra.journal_tracking.repository.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.sra.journal_tracking.entity.jpa.Journal;

@Repository
public interface JournalRepository extends JpaRepository<Journal, UUID> {
    Optional<Journal> findByIssn(String issn);

    Optional<Journal> findByJournalNameIgnoreCase(String journalName);

    long countByIsActiveTrue();

    /** Fuzzy search journal by name (case-insensitive LIKE). */
    @Query("SELECT j FROM Journal j WHERE LOWER(j.journalName) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<Journal> searchByName(@Param("name") String name, Pageable pageable);

    /** Top-tier journals for a field, sorted by impact factor descending (Q1 first). */
    List<Journal> findByField_FieldIdAndIsActiveTrueOrderByImpactFactorDesc(UUID fieldId, Pageable pageable);

    /** Count active journals in a field. */
    long countByField_FieldIdAndIsActiveTrue(UUID fieldId);

    /** All active journals with field eagerly fetched, ordered by impact factor descending. */
    @Query("SELECT j FROM Journal j JOIN FETCH j.field WHERE j.isActive = true ORDER BY j.impactFactor DESC")
    List<Journal> findAllActiveWithFieldOrderByImpactFactorDesc();
}
