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
        addRoleExpiryAtIfMissing();
        addRefreshTokenColumnsIfMissing();
        addAuthorCountryIfMissing();
    }

    private void addRoleExpiryAtIfMissing() {
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

    private void addRefreshTokenColumnsIfMissing() {
        try {
            Integer refreshTokenHashCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sys.columns WHERE object_id = OBJECT_ID(N'USER_SESSION') AND name = N'RefreshTokenHash'",
                    Integer.class);

            if (refreshTokenHashCount != null && refreshTokenHashCount == 0) {
                log.info("=== Adding RefreshTokenHash column to USER_SESSION table ===");
                jdbcTemplate.execute("ALTER TABLE USER_SESSION ADD RefreshTokenHash NVARCHAR(500) NULL");
                log.info("=== RefreshTokenHash column added successfully ===");
            } else {
                log.debug("RefreshTokenHash column already exists - skipping migration");
            }

            Integer refreshExpiresAtCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sys.columns WHERE object_id = OBJECT_ID(N'USER_SESSION') AND name = N'RefreshExpiresAt'",
                    Integer.class);

            if (refreshExpiresAtCount != null && refreshExpiresAtCount == 0) {
                log.info("=== Adding RefreshExpiresAt column to USER_SESSION table ===");
                jdbcTemplate.execute("ALTER TABLE USER_SESSION ADD RefreshExpiresAt DATETIME2 NULL");
                log.info("=== RefreshExpiresAt column added successfully ===");
            } else {
                log.debug("RefreshExpiresAt column already exists - skipping migration");
            }

            Integer refreshTokenIndexCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sys.indexes WHERE object_id = OBJECT_ID(N'USER_SESSION') AND name = N'IX_SESSION_RefreshTokenHash'",
                    Integer.class);

            if (refreshTokenIndexCount != null && refreshTokenIndexCount == 0) {
                log.info("=== Adding IX_SESSION_RefreshTokenHash index to USER_SESSION table ===");
                jdbcTemplate.execute("CREATE INDEX IX_SESSION_RefreshTokenHash ON USER_SESSION(RefreshTokenHash)");
                log.info("=== IX_SESSION_RefreshTokenHash index added successfully ===");
            } else {
                log.debug("IX_SESSION_RefreshTokenHash index already exists - skipping migration");
            }
        } catch (Exception e) {
            log.error("Schema migration for refresh token columns failed: {}", e.getMessage(), e);
        }
    }

    private void addAuthorCountryIfMissing() {
        try {
            Integer countryCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sys.columns WHERE object_id = OBJECT_ID(N'AUTHOR') AND name = N'Country'",
                    Integer.class);

            if (countryCount != null && countryCount == 0) {
                log.info("=== Adding Country column to AUTHOR table ===");
                jdbcTemplate.execute("ALTER TABLE AUTHOR ADD Country NVARCHAR(100) NULL");
                log.info("=== Country column added successfully ===");
            } else {
                log.debug("Country column already exists - skipping migration");
            }

            Integer countryIndexCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sys.indexes WHERE object_id = OBJECT_ID(N'AUTHOR') AND name = N'IX_AUTHOR_Country'",
                    Integer.class);

            if (countryIndexCount != null && countryIndexCount == 0) {
                log.info("=== Adding IX_AUTHOR_Country index to AUTHOR table ===");
                jdbcTemplate.execute("CREATE INDEX IX_AUTHOR_Country ON AUTHOR(Country)");
                log.info("=== IX_AUTHOR_Country index added successfully ===");
            } else {
                log.debug("IX_AUTHOR_Country index already exists - skipping migration");
            }
        } catch (Exception e) {
            log.error("Schema migration for AUTHOR.Country failed: {}", e.getMessage(), e);
        }
    }
}
