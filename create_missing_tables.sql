-- ============================================================
--  Tạo 2 bảng còn thiếu: USER_REPORT + PAPER_REPORT
--  Chạy script này trực tiếp trong SSMS / Azure Data Studio
-- ============================================================
USE JournalTrendDB;
GO

-- ─── USER_REPORT ───────────────────────────────────────────
IF OBJECT_ID('USER_REPORT', 'U') IS NULL
BEGIN
    CREATE TABLE USER_REPORT (
        ReportID            UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),
        UserID              UNIQUEIDENTIFIER NOT NULL,
        ReportType          NVARCHAR(50)     NOT NULL,
        TargetType          NVARCHAR(50)     NULL,
        TargetID            UNIQUEIDENTIFIER NULL,
        Title               NVARCHAR(300)    NOT NULL,
        Description         NVARCHAR(MAX)    NULL,
        Status              NVARCHAR(20)     NOT NULL DEFAULT 'pending',
        AdminNote           NVARCHAR(MAX)    NULL,
        ResolvedByAdminID   UNIQUEIDENTIFIER NULL,
        CreatedAt           DATETIME2        NOT NULL DEFAULT SYSDATETIME(),
        ResolvedAt          DATETIME2        NULL,
        CONSTRAINT PK_USER_REPORT PRIMARY KEY (ReportID),
        CONSTRAINT FK_USER_REPORT_UserID
            FOREIGN KEY (UserID) REFERENCES [USER](UserID),
        CONSTRAINT FK_USER_REPORT_ResolvedBy
            FOREIGN KEY (ResolvedByAdminID) REFERENCES [USER](UserID)
    );
END
GO

IF OBJECT_ID('USER_REPORT', 'U') IS NOT NULL
BEGIN
    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_USER_REPORT_Status'
                   AND object_id = OBJECT_ID('USER_REPORT'))
        CREATE INDEX IX_USER_REPORT_Status ON USER_REPORT(Status, CreatedAt DESC);
    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_USER_REPORT_UserID'
                   AND object_id = OBJECT_ID('USER_REPORT'))
        CREATE INDEX IX_USER_REPORT_UserID ON USER_REPORT(UserID, CreatedAt DESC);
END
GO

-- ─── PAPER_REPORT ──────────────────────────────────────────
IF OBJECT_ID('PAPER_REPORT', 'U') IS NULL
BEGIN
    CREATE TABLE PAPER_REPORT (
        ReportID    UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),
        PaperID     UNIQUEIDENTIFIER NOT NULL,
        UserID      UNIQUEIDENTIFIER NOT NULL,
        Reason      NVARCHAR(50)     NOT NULL,
        Description NVARCHAR(MAX)    NULL,
        ImageUrls   NVARCHAR(MAX)    NULL,
        Status      NVARCHAR(20)     NOT NULL DEFAULT 'PENDING',
        CreatedAt   DATETIME2        NOT NULL DEFAULT SYSDATETIME(),
        UpdatedAt   DATETIME2        NULL,
        CONSTRAINT PK_PAPER_REPORT PRIMARY KEY (ReportID),
        CONSTRAINT FK_PAPER_REPORT_User
            FOREIGN KEY (UserID) REFERENCES [USER](UserID),
        CONSTRAINT CK_PAPER_REPORT_Status
            CHECK (Status IN ('PENDING','REVIEWED','RESOLVED','DISMISSED'))
    );
END
GO

IF OBJECT_ID('PAPER_REPORT', 'U') IS NOT NULL
BEGIN
    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_PAPER_REPORT_PaperID'
                   AND object_id = OBJECT_ID('PAPER_REPORT'))
        CREATE INDEX IX_PAPER_REPORT_PaperID
            ON PAPER_REPORT(PaperID, CreatedAt DESC);
    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UX_PAPER_REPORT_UserPaper'
                   AND object_id = OBJECT_ID('PAPER_REPORT'))
        CREATE UNIQUE INDEX UX_PAPER_REPORT_UserPaper
            ON PAPER_REPORT(UserID, PaperID) WHERE Status = 'PENDING';
END
GO

PRINT 'Done - USER_REPORT + PAPER_REPORT tables created.';
GO
