package com.sra.journal_tracking.repository.jpa;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.sra.journal_tracking.entity.jpa.ApiSource;

@Repository
public interface ApiSourceRepository extends JpaRepository<ApiSource, UUID> {
    Optional<ApiSource> findBySourceName(String sourceName);
}
