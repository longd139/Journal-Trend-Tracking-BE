package com.sra.journal_tracking.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * One-time schema migration to add RoleExpiryAt column if missing.
 * Safe to keep after first run — checks existence first.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SchemaMigrationRunner implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        try {
            // Check if RoleExpiryAt column exists
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sys.columns WHERE object_id = OBJECT_ID(N'USER') AND name = N'RoleExpiryAt'",
                    Integer.class);

            if (count != null && count == 0) {
                log.info("=== Adding RoleExpiryAt column to [USER] table ===");
                jdbcTemplate.execute("ALTER TABLE [USER] ADD RoleExpiryAt DATETIME2 NULL");
                log.info("=== RoleExpiryAt column added successfully ===");
            } else {
                log.debug("RoleExpiryAt column already exists — skipping migration");
            }
        } catch (Exception e) {
            log.error("Schema migration for RoleExpiryAt failed: {}", e.getMessage(), e);
        }
    }
}
