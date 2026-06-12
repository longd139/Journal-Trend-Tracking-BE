package com.sra.journal_tracking.entity.neo4j;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

import lombok.Getter;
import lombok.Setter;

@Node("Keyword")
@Getter
@Setter
public class Neo4jKeyword {

    @Id
    private String keywordId; // UUID dạng String, khớp với SQL KEYWORD.KeywordID

    private String text;      // KeywordText gốc (có thể khác với normalized)

    private String normalizedText; // lowercase, trim — dùng để search
}
