package com.sra.journal_tracking.repository;

import com.sra.journal_tracking.entity.Keyword;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface KeywordRepository extends JpaRepository<Keyword, UUID> {
}
