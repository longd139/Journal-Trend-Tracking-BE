package com.sra.journal_tracking.config;

import org.neo4j.driver.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableNeo4jRepositories(
    basePackages = "com.sra.journal_tracking.repository.neo4j"
)
public class Neo4jConfig {

    /**
     * Custom Neo4j driver Config with aggressive timeouts.
     * <p>
     * Without this, the driver defaults to 30s connection timeout.
     * With a cloud Neo4j instance (AuraDB), every query attempt that fails
     * to connect would block the calling thread for 30+ seconds.
     * <p>
     * 5s is enough for a healthy cloud Neo4j connection over TLS.
     */
    @Bean
    public Config neo4jDriverConfig() {
        return Config.builder()
                .withConnectionTimeout(5, TimeUnit.SECONDS)
                .withConnectionAcquisitionTimeout(5, TimeUnit.SECONDS)
                .withMaxConnectionLifetime(30, TimeUnit.MINUTES)
                .withMaxTransactionRetryTime(5, TimeUnit.SECONDS)
                .build();
    }
}