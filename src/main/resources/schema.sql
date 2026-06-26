-- ============================================================
--  Auto-run by Spring Boot on startup (spring.sql.init.mode)
--  Idempotent — safe to run multiple times
-- ============================================================

-- 1. USER_SEARCH_HISTORY
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
GO

-- 2. TRENDING_TOPIC
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
GO
