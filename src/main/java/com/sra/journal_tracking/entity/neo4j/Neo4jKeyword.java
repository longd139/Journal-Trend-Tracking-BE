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
    private String keywordId; // Khớp với UUID bên SQL Server nhưng lưu dạng String cho dễ vẽ
    private String text;      // keywordText hiển thị trên graph

    // Getters, Setters...
}
