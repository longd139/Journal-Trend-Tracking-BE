package com.sra.journal_tracking.entity.neo4j;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

import lombok.Getter;
import lombok.Setter;

@Node("Paper")
@Getter
@Setter
public class Neo4jPaper {

    @Id
    private String paperId; // Khớp với UUID bên SQL Server nhưng lưu dạng String

    private String title;

    private String doi; // DOI để deduplicate khi sync

    private Integer pubYear; // Năm xuất bản
}
