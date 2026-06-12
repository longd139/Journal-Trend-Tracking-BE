package com.sra.journal_tracking.repository.jpa;

import com.sra.journal_tracking.entity.jpa.SearchKeyword;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SearchKeywordRepository extends JpaRepository<SearchKeyword, UUID> {

    Optional<SearchKeyword> findByNormalizedText(String normalizedText);

    List<SearchKeyword> findAllByOrderBySearchCountDesc(Pageable pageable);
}
