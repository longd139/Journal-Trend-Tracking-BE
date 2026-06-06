package com.sra.journal_tracking.repository.neo4j;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.stereotype.Repository;

import com.sra.journal_tracking.entity.neo4j.Neo4jKeyword;

@Repository
public interface Neo4jKeywordRepository extends Neo4jRepository<Neo4jKeyword, String> {
}
