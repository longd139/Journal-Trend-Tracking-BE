package com.sra.journal_tracking.repository.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.sra.journal_tracking.entity.jpa.PaperKeyword;
import com.sra.journal_tracking.entity.jpa.PaperKeywordId;

@Repository
public interface PaperKeywordRepository extends JpaRepository<PaperKeyword, PaperKeywordId> {
}
