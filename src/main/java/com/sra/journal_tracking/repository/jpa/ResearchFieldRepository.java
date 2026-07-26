package com.sra.journal_tracking.repository.jpa;

import com.sra.journal_tracking.entity.jpa.ResearchField;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ResearchFieldRepository extends JpaRepository<ResearchField, UUID> {
    Optional<ResearchField> findByFieldNameIgnoreCase(String fieldName);

    /** Top-level fields (no parent) that are actively tracked. */
    List<ResearchField> findByParentFieldIsNullAndIsTrackedTrue();

    /** Sub-fields (niches) under a given parent field. */
    List<ResearchField> findByParentField_FieldIdAndIsTrackedTrue(UUID parentFieldId);

    /** Count papers in a specific field (through direct field relationship). */
    @Query("SELECT COUNT(p) FROM ResearchPaper p WHERE p.field.fieldId = :fieldId")
    long countPapersByFieldId(UUID fieldId);

    /**
     * Count distinct papers in a field through the keyword relationship.
     * Most papers imported from OpenAlex have keywords linked to fields
     * rather than a direct field relationship on ResearchPaper.
     */
    @Query("SELECT COUNT(DISTINCT pk.paper.paperId) FROM PaperKeyword pk " +
           "WHERE pk.keyword.field.fieldId = :fieldId")
    long countPapersByFieldIdViaKeywords(UUID fieldId);
}
