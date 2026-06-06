package com.sra.journal_tracking.repository.jpa;

import com.sra.journal_tracking.entity.jpa.Keyword;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface KeywordRepository extends JpaRepository<Keyword, UUID> {
    Optional<Keyword> findByNormalizedText(String normalizedText);
}