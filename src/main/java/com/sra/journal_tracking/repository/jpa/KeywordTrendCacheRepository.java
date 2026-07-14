package com.sra.journal_tracking.repository.jpa;

import com.sra.journal_tracking.entity.jpa.KeywordTrendCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface KeywordTrendCacheRepository extends JpaRepository<KeywordTrendCache, UUID> {

    Optional<KeywordTrendCache> findByNormalizedKeyword(String normalizedKeyword);

    List<KeywordTrendCache> findAllByOrderByUpdatedAtDesc();

    void deleteByNormalizedKeyword(String normalizedKeyword);
}
