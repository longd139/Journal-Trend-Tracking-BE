package com.sra.journal_tracking.entity.neo4j;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

import lombok.Getter;
import lombok.Setter;

@Node("Topic")
@Getter
@Setter
public class Neo4jTopic {

    @Id
    private String topicId; // UUID từ SQL RESEARCH_TOPIC.TopicID

    private String topicName;

    private Double trendScore;

    private Boolean isTrending;
}
