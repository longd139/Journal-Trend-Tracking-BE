package com.sra.journal_tracking.config;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaRepositories(
    basePackages = "com.sra.journal_tracking.repository.jpa" // Chỉ quét Repo của JPA
)
@EntityScan(
    basePackages = "com.sra.journal_tracking.entity.jpa"     // Chỉ quét Entity của JPA
)
public class JpaConfig {
    // Để Spring Boot tự cấu hình DataSource dựa trên application.properties
}
