package com.sra.journal_tracking.repository.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.sra.journal_tracking.entity.jpa.PaperAuthor;
import com.sra.journal_tracking.entity.jpa.PaperAuthorId;

@Repository
public interface PaperAuthorRepository extends JpaRepository<PaperAuthor, PaperAuthorId> {
}
