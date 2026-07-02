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

