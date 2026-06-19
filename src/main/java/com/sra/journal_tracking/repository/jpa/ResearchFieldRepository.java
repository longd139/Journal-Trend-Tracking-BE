package com.sra.journal_tracking.repository.jpa;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.sra.journal_tracking.entity.jpa.ResearchField;

@Repository
public interface ResearchFieldRepository extends JpaRepository<ResearchField, UUID> {
    Optional<ResearchField> findByFieldNameIgnoreCase(String fieldName);
}
