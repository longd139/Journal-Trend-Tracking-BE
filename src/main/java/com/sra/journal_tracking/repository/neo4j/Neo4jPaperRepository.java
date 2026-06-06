package com.sra.journal_tracking.repository.neo4j;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.stereotype.Repository;

import com.sra.journal_tracking.entity.neo4j.Neo4jPaper;

@Repository
public interface Neo4jPaperRepository extends Neo4jRepository<Neo4jPaper, String> {
}
