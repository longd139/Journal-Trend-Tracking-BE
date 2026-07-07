-- ============================================================
--  Auto-run by Spring Boot on startup (spring.sql.init.mode)
--  JDBC-safe — uses sp_executesql, no GO statements
-- ============================================================

-- 1. USER_SEARCH_HISTORY
IF OBJECT_ID('USER_SEARCH_HISTORY', 'U') IS NULL
BEGIN
    EXEC sp_executesql N'
        CREATE TABLE USER_SEARCH_HISTORY (
            SearchHistoryID UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),
            UserID          UNIQUEIDENTIFIER NOT NULL,
            SearchText      NVARCHAR(500) NOT NULL,
            SearchType      NVARCHAR(20)  NOT NULL,
            SearchedAt      DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
            CONSTRAINT PK_USER_SEARCH_HISTORY PRIMARY KEY CLUSTERED (SearchHistoryID),
            CONSTRAINT FK_USH_USER FOREIGN KEY (UserID)
                REFERENCES [USER] (UserID) ON DELETE CASCADE
        )'
    EXEC sp_executesql N'
        CREATE NONCLUSTERED INDEX IX_USH_UserID_SearchedAt
            ON USER_SEARCH_HISTORY (UserID, SearchedAt DESC)'
END

-- 2. TRENDING_TOPIC
IF OBJECT_ID('TRENDING_TOPIC', 'U') IS NULL
BEGIN
    EXEC sp_executesql N'
        CREATE TABLE TRENDING_TOPIC (
            TrendingTopicID UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),
            TopicName       NVARCHAR(300) NOT NULL,
            PaperCount      INT           NOT NULL DEFAULT 0,
            Source          NVARCHAR(50)  NOT NULL DEFAULT ''openalex'',
            DisplayOrder    INT           NOT NULL DEFAULT 0,
            UpdatedAt       DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
            CONSTRAINT PK_TRENDING_TOPIC PRIMARY KEY CLUSTERED (TrendingTopicID)
        )'
    EXEC sp_executesql N'
        CREATE NONCLUSTERED INDEX IX_TRENDING_TOPIC_DisplayOrder
            ON TRENDING_TOPIC (DisplayOrder ASC)'
END

-- 3. USER.BackgroundUrl
IF NOT EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_NAME = 'USER' AND COLUMN_NAME = 'BackgroundUrl'
)
BEGIN
    ALTER TABLE [USER] ADD BackgroundUrl NVARCHAR(500) NULL
END

-- 4. PDF_REQUEST
IF OBJECT_ID('PDF_REQUEST', 'U') IS NULL
   AND OBJECT_ID('RESEARCH_PAPER', 'U') IS NOT NULL
   AND OBJECT_ID('USER', 'U') IS NOT NULL
BEGIN
    EXEC sp_executesql N'
        CREATE TABLE PDF_REQUEST (
            RequestID UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),
            UserID UNIQUEIDENTIFIER NOT NULL,
            PaperID UNIQUEIDENTIFIER NOT NULL,
            Status NVARCHAR(20) NOT NULL DEFAULT ''pending'',
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
                CHECK (Status IN (''pending'', ''fulfilled'', ''rejected''))
        )'
    EXEC sp_executesql N'
        CREATE INDEX IX_PDF_REQUEST_StatusRequestedAt
            ON PDF_REQUEST(Status, RequestedAt DESC)'
    EXEC sp_executesql N'
        CREATE UNIQUE INDEX UX_PDF_REQUEST_UserPaperPending
            ON PDF_REQUEST(UserID, PaperID)
            WHERE Status = ''pending'''
END

-- 5. RESEARCH_FIELD — Seed additional top-level fields if missing
-- 4b. USER_SESSION refresh token columns
IF OBJECT_ID('USER_SESSION', 'U') IS NOT NULL
   AND NOT EXISTS (
       SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
       WHERE TABLE_NAME = 'USER_SESSION' AND COLUMN_NAME = 'RefreshTokenHash'
   )
BEGIN
    ALTER TABLE USER_SESSION ADD RefreshTokenHash NVARCHAR(500) NULL
END

IF OBJECT_ID('USER_SESSION', 'U') IS NOT NULL
   AND NOT EXISTS (
       SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
       WHERE TABLE_NAME = 'USER_SESSION' AND COLUMN_NAME = 'RefreshExpiresAt'
   )
BEGIN
    ALTER TABLE USER_SESSION ADD RefreshExpiresAt DATETIME2 NULL
END

IF OBJECT_ID('USER_SESSION', 'U') IS NOT NULL
   AND NOT EXISTS (
       SELECT 1 FROM sys.indexes
       WHERE name = 'IX_SESSION_RefreshTokenHash'
         AND object_id = OBJECT_ID('USER_SESSION')
   )
BEGIN
    CREATE INDEX IX_SESSION_RefreshTokenHash ON USER_SESSION(RefreshTokenHash)
END

IF OBJECT_ID('AUTHOR', 'U') IS NOT NULL
   AND NOT EXISTS (
       SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
       WHERE TABLE_NAME = 'AUTHOR' AND COLUMN_NAME = 'Country'
   )
BEGIN
    ALTER TABLE AUTHOR ADD Country NVARCHAR(100) NULL
END

IF OBJECT_ID('AUTHOR', 'U') IS NOT NULL
   AND NOT EXISTS (
       SELECT 1 FROM sys.indexes
       WHERE name = 'IX_AUTHOR_Country'
         AND object_id = OBJECT_ID('AUTHOR')
   )
BEGIN
    CREATE INDEX IX_AUTHOR_Country ON AUTHOR(Country)
END

IF NOT EXISTS (SELECT 1 FROM RESEARCH_FIELD WHERE FieldName = N'Engineering')
    INSERT INTO RESEARCH_FIELD (FieldID, ParentFieldID, FieldName, IsTracked, Description)
    VALUES (NEWID(), NULL, N'Engineering', 1, N'Engineering and technology disciplines');

IF NOT EXISTS (SELECT 1 FROM RESEARCH_FIELD WHERE FieldName = N'Medicine')
    INSERT INTO RESEARCH_FIELD (FieldID, ParentFieldID, FieldName, IsTracked, Description)
    VALUES (NEWID(), NULL, N'Medicine', 1, N'Medical and health sciences');

IF NOT EXISTS (SELECT 1 FROM RESEARCH_FIELD WHERE FieldName = N'Physics')
    INSERT INTO RESEARCH_FIELD (FieldID, ParentFieldID, FieldName, IsTracked, Description)
    VALUES (NEWID(), NULL, N'Physics', 1, N'Physics and physical sciences');

IF NOT EXISTS (SELECT 1 FROM RESEARCH_FIELD WHERE FieldName = N'Economics')
    INSERT INTO RESEARCH_FIELD (FieldID, ParentFieldID, FieldName, IsTracked, Description)
    VALUES (NEWID(), NULL, N'Economics', 1, N'Economics and business studies');

-- 6. Composite index for dashboard overview author query:
--    JOIN PAPER_AUTHOR ⋈ RESEARCH_PAPER on PaperID, filtered by CreatedAt
IF OBJECT_ID('PAPER_AUTHOR', 'U') IS NOT NULL
   AND NOT EXISTS (
       SELECT 1 FROM sys.indexes
       WHERE name = 'IX_PAPER_AUTHOR_PaperID_AuthorID'
         AND object_id = OBJECT_ID('PAPER_AUTHOR')
   )
BEGIN
    CREATE INDEX IX_PAPER_AUTHOR_PaperID_AuthorID ON PAPER_AUTHOR(PaperID, AuthorID)
END

-- 7. Index for researcher overview queries — speeds up name-matching
--    WHERE a.FullName = :fullName (exact match, used by author-paper joins)
IF OBJECT_ID('AUTHOR', 'U') IS NOT NULL
   AND NOT EXISTS (
       SELECT 1 FROM sys.indexes
       WHERE name = 'IX_AUTHOR_FullName'
         AND object_id = OBJECT_ID('AUTHOR')
   )
BEGIN
    CREATE INDEX IX_AUTHOR_FullName ON AUTHOR(FullName)
END

-- 8. FOLLOW.AuthorID — allow users to follow individual authors
IF OBJECT_ID('FOLLOW', 'U') IS NOT NULL
   AND NOT EXISTS (
       SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
       WHERE TABLE_NAME = 'FOLLOW' AND COLUMN_NAME = 'AuthorID'
   )
BEGIN
    ALTER TABLE FOLLOW ADD AuthorID UNIQUEIDENTIFIER NULL
    ALTER TABLE FOLLOW ADD CONSTRAINT FK_FOLLOW_Author
        FOREIGN KEY (AuthorID) REFERENCES AUTHOR(AuthorID)

    -- Drop old CHECK constraint (only knew about Journal/Topic/Keyword)
    IF OBJECT_ID('CK_FOLLOW_OneTarget') IS NOT NULL
        ALTER TABLE FOLLOW DROP CONSTRAINT CK_FOLLOW_OneTarget

    -- Recreate to include AuthorID
    ALTER TABLE FOLLOW ADD CONSTRAINT CK_FOLLOW_OneTarget CHECK (
        (CASE WHEN JournalID IS NOT NULL THEN 1 ELSE 0 END +
         CASE WHEN TopicID   IS NOT NULL THEN 1 ELSE 0 END +
         CASE WHEN KeywordID IS NOT NULL THEN 1 ELSE 0 END +
         CASE WHEN AuthorID  IS NOT NULL THEN 1 ELSE 0 END) = 1
    )
END

-- 10. AUTHOR metrics columns (populated from OpenAlex author endpoint during sync)
IF OBJECT_ID('AUTHOR', 'U') IS NOT NULL
   AND NOT EXISTS (
       SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
       WHERE TABLE_NAME = 'AUTHOR' AND COLUMN_NAME = 'I10Index'
   )
BEGIN
    ALTER TABLE AUTHOR ADD I10Index INT NOT NULL DEFAULT 0
END

IF OBJECT_ID('AUTHOR', 'U') IS NOT NULL
   AND NOT EXISTS (
       SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
       WHERE TABLE_NAME = 'AUTHOR' AND COLUMN_NAME = 'WorksCount'
   )
BEGIN
    ALTER TABLE AUTHOR ADD WorksCount INT NOT NULL DEFAULT 0
END

-- 9. Fix CK_FOLLOW_OneTarget constraint — when AuthorID column exists but
--    constraint was created before AuthorID was added (from migration 8)
IF OBJECT_ID('FOLLOW', 'U') IS NOT NULL
   AND EXISTS (
       SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
       WHERE TABLE_NAME = 'FOLLOW' AND COLUMN_NAME = 'AuthorID'
   )
   AND OBJECT_ID('CK_FOLLOW_OneTarget') IS NOT NULL
BEGIN
    ALTER TABLE FOLLOW DROP CONSTRAINT CK_FOLLOW_OneTarget
    ALTER TABLE FOLLOW ADD CONSTRAINT CK_FOLLOW_OneTarget CHECK (
        (CASE WHEN JournalID IS NOT NULL THEN 1 ELSE 0 END +
         CASE WHEN TopicID   IS NOT NULL THEN 1 ELSE 0 END +
         CASE WHEN KeywordID IS NOT NULL THEN 1 ELSE 0 END +
         CASE WHEN AuthorID  IS NOT NULL THEN 1 ELSE 0 END) = 1
    )
END

-- 10. AUTHOR metrics columns (populated from OpenAlex author endpoint during sync)
IF OBJECT_ID('AUTHOR', 'U') IS NOT NULL
   AND NOT EXISTS (
       SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
       WHERE TABLE_NAME = 'AUTHOR' AND COLUMN_NAME = 'I10Index'
   )
BEGIN
    ALTER TABLE AUTHOR ADD I10Index INT NOT NULL DEFAULT 0
END

IF OBJECT_ID('AUTHOR', 'U') IS NOT NULL
   AND NOT EXISTS (
       SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
       WHERE TABLE_NAME = 'AUTHOR' AND COLUMN_NAME = 'WorksCount'
   )
BEGIN
    ALTER TABLE AUTHOR ADD WorksCount INT NOT NULL DEFAULT 0
END

