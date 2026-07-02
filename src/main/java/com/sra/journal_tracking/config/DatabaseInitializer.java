package com.sra.journal_tracking.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * Ensures that required tables exist in SQL Server on application startup.
 * Uses idempotent DDL (IF OBJECT_ID IS NULL) — safe to run every time.
 *
 * We don't use spring.sql.init because Spring's ScriptUtils can't handle
 * SQL Server's T-SQL syntax (IF...BEGIN...END, GO, etc.) reliably.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseInitializer {

    private final DataSource dataSource;

    @PostConstruct
    public void init() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            // ── USER_SEARCH_HISTORY ──
            stmt.execute("""
                    IF OBJECT_ID('USER_SEARCH_HISTORY', 'U') IS NULL
                    BEGIN
                        CREATE TABLE USER_SEARCH_HISTORY (
                            SearchHistoryID UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),
                            UserID          UNIQUEIDENTIFIER NOT NULL,
                            SearchText      NVARCHAR(500) NOT NULL,
                            SearchType      NVARCHAR(20)  NOT NULL,
                            SearchedAt      DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
                            CONSTRAINT PK_USER_SEARCH_HISTORY PRIMARY KEY CLUSTERED (SearchHistoryID),
                            CONSTRAINT FK_USH_USER FOREIGN KEY (UserID)
                                REFERENCES [USER] (UserID) ON DELETE CASCADE
                        );
                        CREATE NONCLUSTERED INDEX IX_USH_UserID_SearchedAt
                            ON USER_SEARCH_HISTORY (UserID, SearchedAt DESC);
                    END
                    """);
            log.info("DB init: USER_SEARCH_HISTORY ensured");

            // ── TRENDING_TOPIC ──
            stmt.execute("""
                    IF OBJECT_ID('TRENDING_TOPIC', 'U') IS NULL
                    BEGIN
                        CREATE TABLE TRENDING_TOPIC (
                            TrendingTopicID UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),
                            TopicName       NVARCHAR(300) NOT NULL,
                            PaperCount      INT           NOT NULL DEFAULT 0,
                            Source          NVARCHAR(50)  NOT NULL DEFAULT 'openalex',
                            DisplayOrder    INT           NOT NULL DEFAULT 0,
                            UpdatedAt       DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
                            CONSTRAINT PK_TRENDING_TOPIC PRIMARY KEY CLUSTERED (TrendingTopicID)
                        );
                        CREATE NONCLUSTERED INDEX IX_TRENDING_TOPIC_DisplayOrder
                            ON TRENDING_TOPIC (DisplayOrder ASC);
                    END
                    """);
            log.info("DB init: TRENDING_TOPIC ensured");

            // ── ROLE_EXPIRY_AT column on USER (researcher trial) ──
            stmt.execute("""
                    IF NOT EXISTS (
                        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
                        WHERE TABLE_NAME = 'USER' AND COLUMN_NAME = 'RoleExpiryAt'
                    )
                    BEGIN
                        ALTER TABLE [USER] ADD RoleExpiryAt DATETIME2 NULL
                    END
                    """);
            log.info("DB init: USER.RoleExpiryAt column ensured");

            stmt.execute("""
                    IF NOT EXISTS (
                        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
                        WHERE TABLE_NAME = 'USER' AND COLUMN_NAME = 'BackgroundUrl'
                    )
                    BEGIN
                        ALTER TABLE [USER] ADD BackgroundUrl NVARCHAR(500) NULL
                    END
                    """);
            log.info("DB init: USER.BackgroundUrl column ensured");

            stmt.execute("""
                    IF OBJECT_ID('PDF_REQUEST', 'U') IS NULL
                    BEGIN
                        CREATE TABLE PDF_REQUEST (
                            RequestID UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),
                            UserID UNIQUEIDENTIFIER NOT NULL,
                            PaperID UNIQUEIDENTIFIER NOT NULL,
                            Status NVARCHAR(20) NOT NULL DEFAULT 'pending',
                            UserMessage NVARCHAR(1000) NULL,
                            AdminNote NVARCHAR(1000) NULL,
                            RequestedAt DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
                            ResolvedAt DATETIME2 NULL,
                            ResolvedByAdminID UNIQUEIDENTIFIER NULL,
                            CONSTRAINT PK_PDF_REQUEST PRIMARY KEY (RequestID),
                            CONSTRAINT FK_PDF_REQUEST_User FOREIGN KEY (UserID)
                                REFERENCES [USER](UserID),
                            CONSTRAINT FK_PDF_REQUEST_Paper FOREIGN KEY (PaperID)
                                REFERENCES RESEARCH_PAPER(PaperID),
                            CONSTRAINT FK_PDF_REQUEST_Admin FOREIGN KEY (ResolvedByAdminID)
                                REFERENCES [USER](UserID),
                            CONSTRAINT CK_PDF_REQUEST_Status
                                CHECK (Status IN ('pending', 'fulfilled', 'rejected'))
                        );
                        CREATE INDEX IX_PDF_REQUEST_StatusRequestedAt
                            ON PDF_REQUEST(Status, RequestedAt DESC);
                        CREATE UNIQUE INDEX UX_PDF_REQUEST_UserPaperPending
                            ON PDF_REQUEST(UserID, PaperID)
                            WHERE Status = 'pending';
                    END
                    """);
            log.info("DB init: PDF_REQUEST table ensured");

        } catch (Exception e) {
            log.error("Database initialization failed: {}", e.getMessage(), e);
        }
    }
}
