package com.sra.journal_tracking.repository.jpa;

import com.sra.journal_tracking.entity.jpa.Keyword;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface KeywordRepository extends JpaRepository<Keyword, UUID> {
    Optional<Keyword> findByNormalizedText(String normalizedText);

    /**
     * Find keyword suggestions by prefix (case-insensitive), ordered by paper count descending.
     * Used for typeahead/autocomplete as the user types in the search box.
     */
    @Query("SELECT k.keywordText FROM Keyword k WHERE LOWER(k.keywordText) LIKE LOWER(CONCAT(:prefix, '%')) ORDER BY k.paperCount DESC")
    List<String> findKeywordSuggestions(@Param("prefix") String prefix, Pageable pageable);
}