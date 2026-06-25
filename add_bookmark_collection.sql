-- =====================================================================
-- ADD BOOKMARK COLLECTION SUPPORT
-- Run this against JournalTrendDB to enable bookmark collections.
-- =====================================================================

-- 1. Create BOOKMARK_COLLECTION table
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'BOOKMARK_COLLECTION')
BEGIN
    CREATE TABLE BOOKMARK_COLLECTION (
        CollectionID    UNIQUEIDENTIFIER    NOT NULL  DEFAULT NEWID(),
        UserID          UNIQUEIDENTIFIER    NOT NULL,
        Name            NVARCHAR(200)       NOT NULL,
        Description     NVARCHAR(500)       NULL,
        CreatedAt       DATETIME2(0)        NOT NULL  DEFAULT SYSDATETIME(),
        UpdatedAt       DATETIME2(0)        NOT NULL  DEFAULT SYSDATETIME(),

        CONSTRAINT PK_BOOKMARK_COLLECTION           PRIMARY KEY (CollectionID),
        CONSTRAINT FK_BC_User                       FOREIGN KEY (UserID)
                                                    REFERENCES [USER](UserID)
                                                    ON DELETE CASCADE,
        CONSTRAINT UQ_BC_User_Name                  UNIQUE (UserID, Name)
    );
END
GO

-- 2. Add CollectionID column to BOOKMARK table
IF NOT EXISTS (
    SELECT * FROM sys.columns
    WHERE Name = 'CollectionID'
    AND Object_ID = Object_ID('BOOKMARK')
)
BEGIN
    ALTER TABLE BOOKMARK
    ADD CollectionID UNIQUEIDENTIFIER NULL;

    ALTER TABLE BOOKMARK
    ADD CONSTRAINT FK_BM_Collection
        FOREIGN KEY (CollectionID)
        REFERENCES BOOKMARK_COLLECTION(CollectionID)
        ON DELETE CASCADE;
END
GO

-- 3. Add index for querying bookmarks by collection
IF NOT EXISTS (
    SELECT * FROM sys.indexes
    WHERE name = 'IX_BOOKMARK_CollectionID'
    AND object_id = OBJECT_ID('BOOKMARK')
)
BEGIN
    CREATE INDEX IX_BOOKMARK_CollectionID ON BOOKMARK(CollectionID);
END
GO

PRINT 'Bookmark Collection migration completed successfully.';
GO
