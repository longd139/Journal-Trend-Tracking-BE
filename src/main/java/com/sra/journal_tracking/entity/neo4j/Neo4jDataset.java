package com.sra.journal_tracking.entity.neo4j;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

import lombok.Getter;
import lombok.Setter;

@Node("Dataset")
@Getter
@Setter
public class Neo4jDataset {

    @Id
    private String datasetId; // UUID deterministic từ normalizedName

    private String datasetName;

    private String normalizedName;
}
