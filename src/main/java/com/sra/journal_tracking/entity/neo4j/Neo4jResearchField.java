package com.sra.journal_tracking.entity.neo4j;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

import lombok.Getter;
import lombok.Setter;

@Node("ResearchField")
@Getter
@Setter
public class Neo4jResearchField {

    @Id
    private String fieldId; // UUID từ SQL RESEARCH_FIELD.FieldID

    private String fieldName;
}
