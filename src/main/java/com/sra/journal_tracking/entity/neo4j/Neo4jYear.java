package com.sra.journal_tracking.entity.neo4j;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

import lombok.Getter;
import lombok.Setter;

@Node("Year")
@Getter
@Setter
public class Neo4jYear {

    @Id
    private Integer year; // VD: 2024, 2025, 2026
}
