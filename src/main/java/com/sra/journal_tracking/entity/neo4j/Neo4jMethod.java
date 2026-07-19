package com.sra.journal_tracking.entity.neo4j;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

import lombok.Getter;
import lombok.Setter;

@Node("Method")
@Getter
@Setter
public class Neo4jMethod {

    @Id
    private String methodId; // UUID deterministic từ normalizedName

    private String methodName;

    private String normalizedName;

    private String category; // architecture, algorithm, technique, framework, other
}
