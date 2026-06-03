package com.sra.journal_tracking.config;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;

@Configuration
@EnableNeo4jRepositories(
    basePackages = "com.sra.journal_tracking.repository.neo4j" // Chỉ quét Repo của Neo4j
)
// Lưu ý: Spring Data Neo4j tự động quét các class có @Node trong toàn bộ project, 
// nhưng việc giới hạn Repo ở đây giúp tránh xung đột với JPA Repository.
public class Neo4jConfig {
}