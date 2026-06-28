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

-- 3. RESEARCH_FIELD — Seed additional top-level fields if missing
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

