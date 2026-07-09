package com.sra.journal_tracking.repository.jpa;

import com.sra.journal_tracking.entity.jpa.PaperCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PaperCacheRepository extends JpaRepository<PaperCache, UUID> {
}
