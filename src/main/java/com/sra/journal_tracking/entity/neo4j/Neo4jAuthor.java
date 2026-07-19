package com.sra.journal_tracking.entity.neo4j;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

import lombok.Getter;
import lombok.Setter;

@Node("Author")
@Getter
@Setter
public class Neo4jAuthor {

    @Id
    private String authorId; // UUID từ SQL AUTHOR.AuthorID

    private String fullName;

    private Integer hIndex;

    private String country;
}
