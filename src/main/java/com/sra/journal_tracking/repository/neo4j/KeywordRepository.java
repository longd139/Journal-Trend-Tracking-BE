package com.sra.journal_tracking.repository.neo4j;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.sra.journal_tracking.entity.jpa.Keyword;

@Repository
public interface KeywordRepository extends JpaRepository<Keyword, UUID> {
}
