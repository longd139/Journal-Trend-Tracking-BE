package com.sra.journal_tracking.repository.jpa;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.sra.journal_tracking.entity.jpa.PaperKeyword;

@Repository
public interface PaperKeywordRepository extends JpaRepository<PaperKeyword, UUID> {
}
