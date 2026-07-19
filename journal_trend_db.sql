-- ============================================================
--  Scientific Journal Publication Trend Tracking System
--  SQL Server Setup Script  |  Version 2.2  |  2026
--  29 Entity Tables + 4 System Tables = 33 Tables Total
-- ============================================================
--  Chay script nay voi quyen sysadmin hoac dbcreator.
--  Script co the chay lai nhieu lan (idempotent).
-- ============================================================

IF DB_ID('JournalTrendDB') IS NULL
    CREATE DATABASE JournalTrendDB
        COLLATE Vietnamese_CI_AI;
GO

USE JournalTrendDB;
GO


-- ============================================================
--  XOA CAC BANG CU (thu tu nguoc FK dependency)
-- ============================================================
DROP TABLE IF EXISTS KEYWORD_TREND_CACHE;
DROP TABLE IF EXISTS TRENDING_TOPIC;
DROP TABLE IF EXISTS SEARCH_KEYWORD;
DROP TABLE IF EXISTS AUTO_SYNC_KEYWORD;
DROP TABLE IF EXISTS VERIFICATION_TOKEN;
DROP TABLE IF EXISTS AUDIT_LOG;
DROP TABLE IF EXISTS USER_USAGE;
DROP TABLE IF EXISTS SYSTEM_CONFIG;
DROP TABLE IF EXISTS DASHBOARD_WIDGET;
DROP TABLE IF EXISTS REPORT;
DROP TABLE IF EXISTS NOTIFICATION;
DROP TABLE IF EXISTS PDF_REQUEST;
DROP TABLE IF EXISTS FOLLOW;
DROP TABLE IF EXISTS BOOKMARK;
DROP TABLE IF EXISTS BOOKMARK_COLLECTION;
DROP TABLE IF EXISTS USER_SEARCH_HISTORY;
DROP TABLE IF EXISTS USER_READING_HISTORY;
DROP TABLE IF EXISTS PUBLICATION_TREND;
DROP TABLE IF EXISTS TOPIC_KEYWORD;
DROP TABLE IF EXISTS RESEARCH_TOPIC;
DROP TABLE IF EXISTS PAPER_KEYWORD;
DROP TABLE IF EXISTS PAPER_AUTHOR;
DROP TABLE IF EXISTS RESEARCH_PAPER;
DROP TABLE IF EXISTS KEYWORD;
DROP TABLE IF EXISTS AUTHOR;
DROP TABLE IF EXISTS JOURNAL;
DROP TABLE IF EXISTS RESEARCH_FIELD;
DROP TABLE IF EXISTS SYNC_LOG;
DROP TABLE IF EXISTS API_SOURCE;
DROP TABLE IF EXISTS USER_SESSION;
DROP TABLE IF EXISTS [USER];
DROP TABLE IF EXISTS ROLE;
GO

-- ============================================================
--  NHOM 1: XAC THUC & NGUOI DUNG (Auth)
-- ============================================================

-- ── ROLE ─────────────────────────────────────────────────────
-- Luu vai tro trong he thong. 3 role co dinh theo model freemium:
-- academic_user (free), researcher (paid), admin
CREATE TABLE ROLE (
    RoleID      UNIQUEIDENTIFIER    NOT NULL  DEFAULT NEWID(),
    RoleName    NVARCHAR(50)        NOT NULL,
    Description NVARCHAR(500)       NULL,

    CONSTRAINT PK_ROLE        PRIMARY KEY (RoleID),
    CONSTRAINT UK_ROLE_Name   UNIQUE      (RoleName)
);
GO

-- ── USER ─────────────────────────────────────────────────────
-- Tai khoan nguoi dung. USER la tu khoa SQL Server nen dung ngoac vuong.
CREATE TABLE [USER] (
    UserID          UNIQUEIDENTIFIER    NOT NULL  DEFAULT NEWID(),
    RoleID          UNIQUEIDENTIFIER    NOT NULL,
    Email           NVARCHAR(255)       NOT NULL,
    PasswordHash    NVARCHAR(255)       NOT NULL,   -- bcrypt hash, khong luu plaintext
    FullName        NVARCHAR(200)       NOT NULL,
    Institution     NVARCHAR(300)       NULL,
    BackgroundUrl   NVARCHAR(500)       NULL,
    IsActive        BIT                 NOT NULL  DEFAULT 1,
    CreatedAt       DATETIME2(0)        NOT NULL  DEFAULT SYSDATETIME(),
    LastLoginAt     DATETIME2(0)        NULL,
    RoleExpiryAt    DATETIME2(0)        NULL,               -- NULL = vinh vien, dung cho researcher trial (3 ngay)

    CONSTRAINT PK_USER          PRIMARY KEY (UserID),
    CONSTRAINT UK_USER_Email    UNIQUE      (Email),
    CONSTRAINT FK_USER_Role     FOREIGN KEY (RoleID) REFERENCES ROLE(RoleID)
);
GO

-- ── USER_SESSION ──────────────────────────────────────────────
-- Quan ly phien dang nhap. Moi lan login tao 1 session moi.
-- Khi dang xuat xoa record. Khi token het han ExpiresAt < SYSDATETIME().
CREATE TABLE USER_SESSION (
    SessionID   UNIQUEIDENTIFIER    NOT NULL  DEFAULT NEWID(),
    UserID      UNIQUEIDENTIFIER    NOT NULL,
    TokenHash   NVARCHAR(500)       NOT NULL,
    RefreshTokenHash NVARCHAR(500)  NULL,
    ExpiresAt   DATETIME2(0)        NOT NULL,
    RefreshExpiresAt DATETIME2(0)   NULL,
    CreatedAt   DATETIME2(0)        NOT NULL  DEFAULT SYSDATETIME(),

    CONSTRAINT PK_USER_SESSION  PRIMARY KEY (SessionID),
    CONSTRAINT FK_SESSION_User  FOREIGN KEY (UserID) REFERENCES [USER](UserID)
                                ON DELETE CASCADE
);
GO

-- ── VERIFICATION_TOKEN ──────────────────────────────────────────
-- Token xac thuc email va reset mat khau.
-- TokenType: EMAIL_VERIFICATION | PASSWORD_RESET
CREATE TABLE VERIFICATION_TOKEN (
    TokenID     UNIQUEIDENTIFIER    NOT NULL  DEFAULT NEWID(),
    UserID      UNIQUEIDENTIFIER    NOT NULL,
    Token       NVARCHAR(255)       NOT NULL,
    TokenType   NVARCHAR(20)        NOT NULL
                    CHECK (TokenType IN ('EMAIL_VERIFICATION','PASSWORD_RESET')),
    ExpiresAt   DATETIME2(0)        NOT NULL,
    CreatedAt   DATETIME2(0)        NOT NULL  DEFAULT SYSDATETIME(),
    IsUsed      BIT                 NOT NULL  DEFAULT 0,

    CONSTRAINT PK_VERIFICATION_TOKEN    PRIMARY KEY (TokenID),
    CONSTRAINT UK_VT_Token              UNIQUE      (Token),
    CONSTRAINT FK_VT_User               FOREIGN KEY (UserID) REFERENCES [USER](UserID)
                                        ON DELETE CASCADE
);
GO

-- ============================================================
--  NHOM 2: DU LIEU HOC THUAT (Academic Data)
-- ============================================================

-- ── API_SOURCE ────────────────────────────────────────────────
-- Cau hinh cac nguon API hoc thuat: Semantic Scholar, OpenAlex, Crossref
CREATE TABLE API_SOURCE (
    SourceID        UNIQUEIDENTIFIER    NOT NULL  DEFAULT NEWID(),
    SourceName      NVARCHAR(100)       NOT NULL,
    BaseURL         NVARCHAR(500)       NOT NULL,
    IsActive        BIT                 NOT NULL  DEFAULT 1,
    RateLimitRPM    INT                 NULL,
    LastSyncedAt    DATETIME2(0)        NULL,

    CONSTRAINT PK_API_SOURCE        PRIMARY KEY (SourceID),
    CONSTRAINT UK_API_SOURCE_Name   UNIQUE      (SourceName)
);
GO

-- ── SYNC_LOG ─────────────────────────────────────────────────
-- Lich su moi lan dong bo du lieu tu API.
-- IsManual = 1 khi Admin kich hoat thu cong (UC-22).
CREATE TABLE SYNC_LOG (
    LogID           UNIQUEIDENTIFIER    NOT NULL  DEFAULT NEWID(),
    SourceID        UNIQUEIDENTIFIER    NOT NULL,
    SyncType        NVARCHAR(15)        NOT NULL
                        CHECK (SyncType IN ('full','incremental')),
    IsManual        BIT                 NOT NULL  DEFAULT 0,
    Status          NVARCHAR(20)        NOT NULL  DEFAULT 'running'
                        CHECK (Status IN ('running','completed','failed')),
    PapersFetched   INT                 NULL  DEFAULT 0,
    PapersInserted  INT                 NULL  DEFAULT 0,
    ErrorMessage    NVARCHAR(MAX)       NULL,
    StartedAt       DATETIME2(0)        NOT NULL  DEFAULT SYSDATETIME(),
    CompletedAt     DATETIME2(0)        NULL,

    CONSTRAINT PK_SYNC_LOG      PRIMARY KEY (LogID),
    CONSTRAINT FK_SYNC_Source   FOREIGN KEY (SourceID) REFERENCES API_SOURCE(SourceID)
);
GO

-- ── RESEARCH_FIELD ────────────────────────────────────────────
-- Phan loai linh vuc theo cay phan cap.
-- Vi du: Computer Science -> AI -> Machine Learning
CREATE TABLE RESEARCH_FIELD (
    FieldID         UNIQUEIDENTIFIER    NOT NULL  DEFAULT NEWID(),
    ParentFieldID   UNIQUEIDENTIFIER    NULL,               -- NULL = node goc
    FieldName       NVARCHAR(200)       NOT NULL,
    IsTracked       BIT                 NOT NULL  DEFAULT 1,
    Description     NVARCHAR(500)       NULL,

    CONSTRAINT PK_RESEARCH_FIELD    PRIMARY KEY (FieldID),
    CONSTRAINT UK_FIELD_Name        UNIQUE      (FieldName),
    CONSTRAINT FK_FIELD_Parent      FOREIGN KEY (ParentFieldID)
                                    REFERENCES RESEARCH_FIELD(FieldID)
);
GO

-- ── JOURNAL ───────────────────────────────────────────────────
-- Tap chi khoa hoc. ISSN la unique identifier chuan quoc te.
CREATE TABLE JOURNAL (
    JournalID       UNIQUEIDENTIFIER    NOT NULL  DEFAULT NEWID(),
    SourceID        UNIQUEIDENTIFIER    NOT NULL,
    FieldID         UNIQUEIDENTIFIER    NULL,
    JournalName     NVARCHAR(500)       NOT NULL,
    ISSN            NVARCHAR(20)        NULL,
    Publisher       NVARCHAR(300)       NULL,
    ImpactFactor    DECIMAL(8,3)        NULL,
    Quartile        CHAR(2)             NULL
                        CHECK (Quartile IN ('Q1','Q2','Q3','Q4')),
    IsActive        BIT                 NOT NULL  DEFAULT 1,

    CONSTRAINT PK_JOURNAL           PRIMARY KEY (JournalID),
    CONSTRAINT FK_JOURNAL_Source    FOREIGN KEY (SourceID) REFERENCES API_SOURCE(SourceID),
    CONSTRAINT FK_JOURNAL_Field     FOREIGN KEY (FieldID)  REFERENCES RESEARCH_FIELD(FieldID)
);
GO

-- ── AUTHOR ────────────────────────────────────────────────────
-- Tac gia bai bao. ExternalAuthorID la ID goc tu API nguon.
CREATE TABLE AUTHOR (
    AuthorID            UNIQUEIDENTIFIER    NOT NULL  DEFAULT NEWID(),
    SourceID            UNIQUEIDENTIFIER    NOT NULL,
    ExternalAuthorID    NVARCHAR(200)       NULL,
    FullName            NVARCHAR(300)       NOT NULL,
    Affiliation         NVARCHAR(500)       NULL,
    Country             NVARCHAR(100)       NULL,
    HIndex              INT                 NULL  DEFAULT 0,
    TotalCitations      INT                 NULL  DEFAULT 0,
    I10Index            INT                 NULL  DEFAULT 0,
    WorksCount          INT                 NULL  DEFAULT 0,

    CONSTRAINT PK_AUTHOR            PRIMARY KEY (AuthorID),
    CONSTRAINT FK_AUTHOR_Source     FOREIGN KEY (SourceID) REFERENCES API_SOURCE(SourceID)
);
GO

-- ── KEYWORD ───────────────────────────────────────────────────
-- Tu khoa nghien cuu. UK tren NormalizedText de tranh trung do casing.
CREATE TABLE KEYWORD (
    KeywordID       UNIQUEIDENTIFIER    NOT NULL  DEFAULT NEWID(),
    FieldID         UNIQUEIDENTIFIER    NULL,
    KeywordText     NVARCHAR(300)       NOT NULL,
    NormalizedText  NVARCHAR(300)       NOT NULL,           -- lowercase, trim
    PaperCount      INT                 NOT NULL  DEFAULT 0,

    CONSTRAINT PK_KEYWORD               PRIMARY KEY (KeywordID),
    CONSTRAINT UK_KEYWORD_Normalized    UNIQUE      (NormalizedText),
    CONSTRAINT FK_KEYWORD_Field         FOREIGN KEY (FieldID) REFERENCES RESEARCH_FIELD(FieldID)
);
GO

-- ── RESEARCH_PAPER ────────────────────────────────────────────
-- Bang trung tam. Chi luu metadata, khong luu full-text.
-- DOI la dinh danh chuan toan cau, dung de tranh import trung.
CREATE TABLE RESEARCH_PAPER (
    PaperID         UNIQUEIDENTIFIER    NOT NULL  DEFAULT NEWID(),
    SourceID        UNIQUEIDENTIFIER    NOT NULL,
    JournalID       UNIQUEIDENTIFIER    NULL,
    FieldID         UNIQUEIDENTIFIER    NULL,
    Title           NVARCHAR(1000)      NOT NULL,
    Abstract        NVARCHAR(MAX)       NULL,
    DOI             NVARCHAR(200)       NULL,
    PubDate         DATE                NULL,
    PubYear         SMALLINT            NULL,
    CitationCount   INT                 NOT NULL  DEFAULT 0,
    IsOpenAccess    BIT                 NOT NULL  DEFAULT 0,
    PdfUrl          NVARCHAR(500)       NULL,
    AiSummary       NVARCHAR(MAX)       NULL,
    AiSummarySections NVARCHAR(MAX)     NULL,   -- JSON: [{"heading":"Context & Problem","content":"..."}]
    Methodology     NVARCHAR(100)       NULL,   -- "RCT", "case study", "systematic review"...
    CreatedAt       DATETIME2(0)        NOT NULL  DEFAULT SYSDATETIME(),

    CONSTRAINT PK_RESEARCH_PAPER    PRIMARY KEY (PaperID),
    CONSTRAINT FK_PAPER_Source      FOREIGN KEY (SourceID)  REFERENCES API_SOURCE(SourceID),
    CONSTRAINT FK_PAPER_Journal     FOREIGN KEY (JournalID) REFERENCES JOURNAL(JournalID),
    CONSTRAINT FK_PAPER_Field       FOREIGN KEY (FieldID)   REFERENCES RESEARCH_FIELD(FieldID),
    CONSTRAINT CK_PAPER_PubYear     CHECK       (PubYear BETWEEN 1900 AND 2100)
);
GO

-- ── PAPER_AUTHOR ──────────────────────────────────────────────
-- Junction: RESEARCH_PAPER <-> AUTHOR (N-N).
CREATE TABLE PAPER_AUTHOR (
    PaperID         UNIQUEIDENTIFIER    NOT NULL,
    AuthorID        UNIQUEIDENTIFIER    NOT NULL,
    AuthorOrder     SMALLINT            NOT NULL  DEFAULT 1,
    IsCorresponding BIT                 NOT NULL  DEFAULT 0,

    CONSTRAINT PK_PAPER_AUTHOR      PRIMARY KEY (PaperID, AuthorID),
    CONSTRAINT FK_PA_Paper          FOREIGN KEY (PaperID)  REFERENCES RESEARCH_PAPER(PaperID)
                                    ON DELETE CASCADE,
    CONSTRAINT FK_PA_Author         FOREIGN KEY (AuthorID) REFERENCES AUTHOR(AuthorID),
    CONSTRAINT CK_PA_AuthorOrder    CHECK       (AuthorOrder >= 1)
);
GO

-- ── PAPER_KEYWORD ─────────────────────────────────────────────
-- Junction: RESEARCH_PAPER <-> KEYWORD (N-N).
CREATE TABLE PAPER_KEYWORD (
    PaperID         UNIQUEIDENTIFIER    NOT NULL,
    KeywordID       UNIQUEIDENTIFIER    NOT NULL,
    RelevanceScore  DECIMAL(5,4)        NULL,               -- 0.0000 - 1.0000

    CONSTRAINT PK_PAPER_KEYWORD         PRIMARY KEY (PaperID, KeywordID),
    CONSTRAINT FK_PK_Paper              FOREIGN KEY (PaperID)   REFERENCES RESEARCH_PAPER(PaperID)
                                        ON DELETE CASCADE,
    CONSTRAINT FK_PK_Keyword            FOREIGN KEY (KeywordID) REFERENCES KEYWORD(KeywordID),
    CONSTRAINT CK_PK_RelevanceScore     CHECK (RelevanceScore IS NULL
                                           OR RelevanceScore BETWEEN 0 AND 1)
);
GO

-- ============================================================
--  NHOM 3: PHAN TICH XU HUONG (Trend Analysis)
-- ============================================================

-- ── RESEARCH_TOPIC ────────────────────────────────────────────
-- Chu de nghien cuu tong hop tu nhieu keyword.
-- TrendScore duoc Scheduler tinh lai moi chu ky sync.
CREATE TABLE RESEARCH_TOPIC (
    TopicID     UNIQUEIDENTIFIER    NOT NULL  DEFAULT NEWID(),
    FieldID     UNIQUEIDENTIFIER    NULL,
    TopicName   NVARCHAR(300)       NOT NULL,
    IsTrending  BIT                 NOT NULL  DEFAULT 0,
    TrendScore  DECIMAL(10,4)       NOT NULL  DEFAULT 0,
    PaperCount  INT                 NOT NULL  DEFAULT 0,
    UpdatedAt   DATETIME2(0)        NOT NULL  DEFAULT SYSDATETIME(),

    CONSTRAINT PK_RESEARCH_TOPIC    PRIMARY KEY (TopicID),
    CONSTRAINT FK_TOPIC_Field       FOREIGN KEY (FieldID) REFERENCES RESEARCH_FIELD(FieldID)
);
GO

-- ── TOPIC_KEYWORD ─────────────────────────────────────────────
-- Junction: RESEARCH_TOPIC <-> KEYWORD (N-N).
CREATE TABLE TOPIC_KEYWORD (
    TopicID     UNIQUEIDENTIFIER    NOT NULL,
    KeywordID   UNIQUEIDENTIFIER    NOT NULL,
    Weight      DECIMAL(5,4)        NOT NULL  DEFAULT 1.0,

    CONSTRAINT PK_TOPIC_KEYWORD PRIMARY KEY (TopicID, KeywordID),
    CONSTRAINT FK_TK_Topic      FOREIGN KEY (TopicID)   REFERENCES RESEARCH_TOPIC(TopicID)
                                ON DELETE CASCADE,
    CONSTRAINT FK_TK_Keyword    FOREIGN KEY (KeywordID) REFERENCES KEYWORD(KeywordID),
    CONSTRAINT CK_TK_Weight     CHECK (Weight BETWEEN 0 AND 1)
);
GO

-- ── PUBLICATION_TREND ─────────────────────────────────────────
-- Snapshot xu huong cong bo theo tung ky. Nguon du lieu cho
-- tat ca bieu do xu huong trong he thong.
-- Dung cap (TrendTarget, TargetID) thay 4 FK nullable
-- de tranh polymorphic association anti-pattern.
CREATE TABLE PUBLICATION_TREND (
    TrendID         UNIQUEIDENTIFIER    NOT NULL  DEFAULT NEWID(),
    PeriodType      NVARCHAR(10)        NOT NULL
                        CHECK (PeriodType IN ('monthly','quarterly','yearly')),
    PeriodValue     NVARCHAR(15)        NOT NULL,           -- '2024-01' | '2024-Q1' | '2024'
    TrendTarget     NVARCHAR(10)        NOT NULL
                        CHECK (TrendTarget IN ('keyword','topic','journal','field')),
    TargetID        UNIQUEIDENTIFIER    NOT NULL,
    PaperCount      INT                 NOT NULL  DEFAULT 0,
    CitationCount   INT                 NOT NULL  DEFAULT 0,
    GrowthRate      DECIMAL(10,4)       NULL,               -- % so voi ky truoc, am = giam
    CalculatedAt    DATETIME2(0)        NOT NULL  DEFAULT SYSDATETIME(),

    CONSTRAINT PK_PUBLICATION_TREND PRIMARY KEY (TrendID),
    CONSTRAINT UK_TREND_Period      UNIQUE (TrendTarget, TargetID, PeriodType, PeriodValue)
);
GO

-- ── SEARCH_KEYWORD ──────────────────────────────────────────────
-- Theo doi tu khoa duoc tim kiem nhieu nhat (hot keyword tracking).
-- SearchKeywordService ghi nhan moi lan nguoi dung search.
CREATE TABLE SEARCH_KEYWORD (
    SearchKeywordID     UNIQUEIDENTIFIER    NOT NULL  DEFAULT NEWID(),
    KeywordText         NVARCHAR(500)       NOT NULL,
    NormalizedText      NVARCHAR(500)       NOT NULL,
    SearchCount         INT                 NOT NULL  DEFAULT 1,
    LastSearchedAt      DATETIME2(0)        NOT NULL  DEFAULT SYSDATETIME(),
    CreatedAt           DATETIME2(0)        NOT NULL  DEFAULT SYSDATETIME(),

    CONSTRAINT PK_SEARCH_KEYWORD            PRIMARY KEY (SearchKeywordID),
    CONSTRAINT UK_SK_NormalizedText         UNIQUE      (NormalizedText)
);
GO

-- ── TRENDING_TOPIC ─────────────────────────────────────────────
-- Bang trending topics doc lap, hien thi tren dashboard xu huong.
-- Duoc cap nhat boi scheduler hoac admin.
CREATE TABLE TRENDING_TOPIC (
    TrendingTopicID     UNIQUEIDENTIFIER    NOT NULL  DEFAULT NEWID(),
    TopicName           NVARCHAR(300)       NOT NULL,
    PaperCount          INT                 NOT NULL  DEFAULT 0,
    Source              NVARCHAR(50)        NOT NULL,
    DisplayOrder        INT                 NOT NULL  DEFAULT 0,
    UpdatedAt           DATETIME2(0)        NOT NULL  DEFAULT SYSDATETIME(),

    CONSTRAINT PK_TRENDING_TOPIC            PRIMARY KEY (TrendingTopicID)
);
GO

-- ============================================================
--  NHOM 4: TUONG TAC NGUOI DUNG (User Interaction)
-- ============================================================

-- ── BOOKMARK_COLLECTION ───────────────────────────────────────
-- Bo suu tap bookmark. User co the nhom cac bookmark vao collection.
CREATE TABLE BOOKMARK_COLLECTION (
    CollectionID            UNIQUEIDENTIFIER    NOT NULL  DEFAULT NEWID(),
    UserID                  UNIQUEIDENTIFIER    NOT NULL,
    Name                    NVARCHAR(200)       NOT NULL,
    Description             NVARCHAR(500)       NULL,
    LastNotifiedMilestone   INT                 NOT NULL  DEFAULT 0,
    CreatedAt               DATETIME2(0)        NOT NULL  DEFAULT SYSDATETIME(),
    UpdatedAt               DATETIME2(0)        NOT NULL  DEFAULT SYSDATETIME(),

    CONSTRAINT PK_BOOKMARK_COLLECTION           PRIMARY KEY (CollectionID),
    CONSTRAINT FK_BC_User                       FOREIGN KEY (UserID) REFERENCES [USER](UserID)
                                                ON DELETE CASCADE
);
GO

-- ── BOOKMARK ─────────────────────────────────────────────────
-- User luu bai bao, keyword hoac collection.
-- Bookmark khong tinh vao usage limit.
-- Co the thuoc ve 1 Collection (tuychon).
CREATE TABLE BOOKMARK (
    BookmarkID              UNIQUEIDENTIFIER    NOT NULL  DEFAULT NEWID(),
    UserID                  UNIQUEIDENTIFIER    NOT NULL,
    PaperID                 UNIQUEIDENTIFIER    NULL,
    KeywordID               UNIQUEIDENTIFIER    NULL,
    CollectionID            UNIQUEIDENTIFIER    NULL,
    Notes                   NVARCHAR(500)       NULL,
    LastNotifiedMilestone   INT                 NOT NULL  DEFAULT 0,
    CreatedAt               DATETIME2(0)        NOT NULL  DEFAULT SYSDATETIME(),

    CONSTRAINT PK_BOOKMARK              PRIMARY KEY (BookmarkID),
    CONSTRAINT CK_BOOKMARK_OneTarget    CHECK (
        (PaperID IS NOT NULL AND KeywordID IS NULL AND CollectionID IS NULL) OR
        (PaperID IS NULL     AND KeywordID IS NOT NULL AND CollectionID IS NULL) OR
        (PaperID IS NULL     AND KeywordID IS NULL     AND CollectionID IS NOT NULL)
    ),
    CONSTRAINT FK_BM_User               FOREIGN KEY (UserID)       REFERENCES [USER](UserID)
                                        ON DELETE CASCADE,
    CONSTRAINT FK_BM_Paper              FOREIGN KEY (PaperID)      REFERENCES RESEARCH_PAPER(PaperID),
    CONSTRAINT FK_BM_Keyword            FOREIGN KEY (KeywordID)    REFERENCES KEYWORD(KeywordID),
    CONSTRAINT FK_BM_Collection         FOREIGN KEY (CollectionID) REFERENCES BOOKMARK_COLLECTION(CollectionID)
);
GO

-- ── FOLLOW ────────────────────────────────────────────────────
-- User theo doi journal, topic, keyword hoac author.
-- Dung 1 trong 4 FK. Follow khong tinh usage limit.
CREATE TABLE FOLLOW (
    FollowID        UNIQUEIDENTIFIER    NOT NULL  DEFAULT NEWID(),
    UserID          UNIQUEIDENTIFIER    NOT NULL,
    JournalID       UNIQUEIDENTIFIER    NULL,
    TopicID         UNIQUEIDENTIFIER    NULL,
    KeywordID       UNIQUEIDENTIFIER    NULL,
    AuthorID        UNIQUEIDENTIFIER    NULL,
    NotifyEnabled   BIT                 NOT NULL  DEFAULT 1,
    CreatedAt       DATETIME2(0)        NOT NULL  DEFAULT SYSDATETIME(),

    CONSTRAINT PK_FOLLOW                PRIMARY KEY (FollowID),
    CONSTRAINT CK_FOLLOW_OneTarget      CHECK (
        (JournalID IS NOT NULL AND TopicID IS NULL     AND KeywordID IS NULL     AND AuthorID IS NULL) OR
        (JournalID IS NULL     AND TopicID IS NOT NULL AND KeywordID IS NULL     AND AuthorID IS NULL) OR
        (JournalID IS NULL     AND TopicID IS NULL     AND KeywordID IS NOT NULL AND AuthorID IS NULL) OR
        (JournalID IS NULL     AND TopicID IS NULL     AND KeywordID IS NULL     AND AuthorID IS NOT NULL)
    ),
    CONSTRAINT FK_FOLLOW_User           FOREIGN KEY (UserID)    REFERENCES [USER](UserID)
                                        ON DELETE CASCADE,
    CONSTRAINT FK_FOLLOW_Journal        FOREIGN KEY (JournalID) REFERENCES JOURNAL(JournalID),
    CONSTRAINT FK_FOLLOW_Topic          FOREIGN KEY (TopicID)   REFERENCES RESEARCH_TOPIC(TopicID),
    CONSTRAINT FK_FOLLOW_Keyword        FOREIGN KEY (KeywordID) REFERENCES KEYWORD(KeywordID),
    CONSTRAINT FK_FOLLOW_Author         FOREIGN KEY (AuthorID)  REFERENCES AUTHOR(AuthorID)
);
GO

-- ── NOTIFICATION ──────────────────────────────────────────────
-- Thong bao den user. upgrade_prompt = nhac nang cap khi het luot.
CREATE TABLE NOTIFICATION (
    NotifID             UNIQUEIDENTIFIER    NOT NULL  DEFAULT NEWID(),
    UserID              UNIQUEIDENTIFIER    NOT NULL,
    [Type]              NVARCHAR(20)        NOT NULL
                            CHECK ([Type] IN (
                                'new_paper',
                                'trend_alert',
                                'system',
                                'upgrade_prompt'   -- nhac Academic User nang cap
                            )),
    Title               NVARCHAR(300)       NOT NULL,
    [Message]           NVARCHAR(MAX)       NULL,
    RelatedPaperID      UNIQUEIDENTIFIER    NULL,
    RelatedJournalID    UNIQUEIDENTIFIER    NULL,
    RelatedTopicID      UNIQUEIDENTIFIER    NULL,
    RelatedKeywordID    UNIQUEIDENTIFIER    NULL,
    IsRead              BIT                 NOT NULL  DEFAULT 0,
    CreatedAt           DATETIME2(0)        NOT NULL  DEFAULT SYSDATETIME(),

    CONSTRAINT PK_NOTIFICATION      PRIMARY KEY (NotifID),
    CONSTRAINT FK_NOTIF_User        FOREIGN KEY (UserID)
                                    REFERENCES [USER](UserID) ON DELETE CASCADE,
    CONSTRAINT FK_NOTIF_Paper       FOREIGN KEY (RelatedPaperID)
                                    REFERENCES RESEARCH_PAPER(PaperID),
    CONSTRAINT FK_NOTIF_Journal     FOREIGN KEY (RelatedJournalID)
                                    REFERENCES JOURNAL(JournalID),
    CONSTRAINT FK_NOTIF_Topic       FOREIGN KEY (RelatedTopicID)
                                    REFERENCES RESEARCH_TOPIC(TopicID),
    CONSTRAINT FK_NOTIF_Keyword     FOREIGN KEY (RelatedKeywordID)
                                    REFERENCES KEYWORD(KeywordID)
);
GO

-- ── PDF_REQUEST ──────────────────────────────────────────────
-- User requests for missing paper PDFs. Admin can resolve manually or accept a found candidate.
CREATE TABLE PDF_REQUEST (
    RequestID           UNIQUEIDENTIFIER    NOT NULL  DEFAULT NEWID(),
    UserID              UNIQUEIDENTIFIER    NOT NULL,
    PaperID             UNIQUEIDENTIFIER    NOT NULL,
    Status              NVARCHAR(20)        NOT NULL  DEFAULT 'pending'
                            CHECK (Status IN ('pending', 'fulfilled', 'rejected')),
    UserMessage         NVARCHAR(1000)      NULL,
    AdminNote           NVARCHAR(1000)      NULL,
    RequestedAt         DATETIME2(0)        NOT NULL  DEFAULT SYSDATETIME(),
    ResolvedAt          DATETIME2(0)        NULL,
    ResolvedByAdminID   UNIQUEIDENTIFIER    NULL,

    CONSTRAINT PK_PDF_REQUEST        PRIMARY KEY (RequestID),
    CONSTRAINT FK_PDF_REQUEST_User   FOREIGN KEY (UserID)
                                      REFERENCES [USER](UserID),
    CONSTRAINT FK_PDF_REQUEST_Paper  FOREIGN KEY (PaperID)
                                      REFERENCES RESEARCH_PAPER(PaperID),
    CONSTRAINT FK_PDF_REQUEST_Admin  FOREIGN KEY (ResolvedByAdminID)
                                      REFERENCES [USER](UserID)
);
GO

-- ── USER_READING_HISTORY ─────────────────────────────────────
-- Theo doi bai bao nao da duoc user xem.
-- Ghi nhan moi lan user goi GET /api/v1/papers/{paperId}.
CREATE TABLE USER_READING_HISTORY (
    ReadingHistoryID    UNIQUEIDENTIFIER    NOT NULL  DEFAULT NEWID(),
    UserID              UNIQUEIDENTIFIER    NOT NULL,
    PaperID             UNIQUEIDENTIFIER    NOT NULL,
    ViewedAt            DATETIME2(0)        NOT NULL  DEFAULT SYSDATETIME(),

    CONSTRAINT PK_USER_READING_HISTORY      PRIMARY KEY (ReadingHistoryID),
    CONSTRAINT FK_URH_User                  FOREIGN KEY (UserID)  REFERENCES [USER](UserID)
                                            ON DELETE CASCADE,
    CONSTRAINT FK_URH_Paper                 FOREIGN KEY (PaperID) REFERENCES RESEARCH_PAPER(PaperID)
);
GO

-- ── USER_SEARCH_HISTORY ────────────────────────────────────────
-- Lich su tim kiem cua nguoi dung.
-- SearchType: KEYWORD | AUTHOR | JOURNAL
CREATE TABLE USER_SEARCH_HISTORY (
    SearchHistoryID     UNIQUEIDENTIFIER    NOT NULL  DEFAULT NEWID(),
    UserID              UNIQUEIDENTIFIER    NOT NULL,
    SearchText          NVARCHAR(500)       NOT NULL,
    SearchType          NVARCHAR(20)        NOT NULL
                            CHECK (SearchType IN ('KEYWORD','AUTHOR','JOURNAL')),
    SearchedAt          DATETIME2(0)        NOT NULL  DEFAULT SYSDATETIME(),

    CONSTRAINT PK_USER_SEARCH_HISTORY       PRIMARY KEY (SearchHistoryID),
    CONSTRAINT FK_USH_User                  FOREIGN KEY (UserID) REFERENCES [USER](UserID)
                                            ON DELETE CASCADE
);
GO

-- ============================================================
--  NHOM 5: BAO CAO & DASHBOARD (chi Researcher)
-- ============================================================

-- ── REPORT ────────────────────────────────────────────────────
-- Bao cao phan tich do Researcher tao (UC-18).
CREATE TABLE REPORT (
    ReportID    UNIQUEIDENTIFIER    NOT NULL  DEFAULT NEWID(),
    UserID      UNIQUEIDENTIFIER    NOT NULL,
    FieldID     UNIQUEIDENTIFIER    NULL,
    ReportName  NVARCHAR(300)       NOT NULL,
    PeriodStart DATE                NOT NULL,
    PeriodEnd   DATE                NOT NULL,
    Status      NVARCHAR(20)        NOT NULL  DEFAULT 'generating'
                    CHECK (Status IN ('generating','completed','failed')),
    Format      NVARCHAR(5)         NOT NULL  DEFAULT 'pdf'
                    CHECK (Format IN ('pdf','csv')),
    FileURL     NVARCHAR(1000)      NULL,
    CreatedAt   DATETIME2(0)        NOT NULL  DEFAULT SYSDATETIME(),

    CONSTRAINT PK_REPORT        PRIMARY KEY (ReportID),
    CONSTRAINT FK_REPORT_User   FOREIGN KEY (UserID)  REFERENCES [USER](UserID)
                                ON DELETE CASCADE,
    CONSTRAINT FK_REPORT_Field  FOREIGN KEY (FieldID) REFERENCES RESEARCH_FIELD(FieldID),
    CONSTRAINT CK_REPORT_Period CHECK (PeriodEnd >= PeriodStart)
);
GO

-- ── DASHBOARD_WIDGET ─────────────────────────────────────────
-- Widget bieu do tren dashboard ca nhan cua Researcher (UC-19).
CREATE TABLE DASHBOARD_WIDGET (
    WidgetID    UNIQUEIDENTIFIER    NOT NULL  DEFAULT NEWID(),
    UserID      UNIQUEIDENTIFIER    NOT NULL,
    WidgetType  NVARCHAR(30)        NOT NULL
                    CHECK (WidgetType IN ('line_chart','bar_chart','pie_chart','stat_card')),
    Title       NVARCHAR(200)       NOT NULL,
    Config      NVARCHAR(MAX)       NULL,                   -- JSON: { field, keyword, period }
    PositionX   INT                 NOT NULL  DEFAULT 0,
    PositionY   INT                 NOT NULL  DEFAULT 0,
    Width       INT                 NOT NULL  DEFAULT 4,
    Height      INT                 NOT NULL  DEFAULT 3,
    CreatedAt   DATETIME2(0)        NOT NULL  DEFAULT SYSDATETIME(),
    UpdatedAt   DATETIME2(0)        NOT NULL  DEFAULT SYSDATETIME(),

    CONSTRAINT PK_DASHBOARD_WIDGET  PRIMARY KEY (WidgetID),
    CONSTRAINT FK_DW_User           FOREIGN KEY (UserID) REFERENCES [USER](UserID)
                                    ON DELETE CASCADE,
    CONSTRAINT CK_DW_Position       CHECK (PositionX >= 0 AND PositionY >= 0),
    CONSTRAINT CK_DW_Size           CHECK (Width >= 1 AND Height >= 1)
);
GO

-- ============================================================
--  BANG HE THONG (System Tables)
-- ============================================================

-- ── KEYWORD_TREND_CACHE ──────────────────────────────────────
-- Luu tru bao cao xu huong tu khoa da generate duoi dang JSON.
-- Tranh phai tinh toan lai bao cao ton kem moi lan user xem lich su.
CREATE TABLE KEYWORD_TREND_CACHE (
    CacheID             UNIQUEIDENTIFIER    NOT NULL  DEFAULT NEWID(),
    Keyword             NVARCHAR(500)       NOT NULL,
    NormalizedKeyword   NVARCHAR(500)       NOT NULL,
    ReportData          NVARCHAR(MAX)       NOT NULL,   -- JSON report
    CreatedAt           DATETIME2(0)        NOT NULL  DEFAULT SYSDATETIME(),
    UpdatedAt           DATETIME2(0)        NOT NULL  DEFAULT SYSDATETIME(),

    CONSTRAINT PK_KEYWORD_TREND_CACHE          PRIMARY KEY (CacheID),
    CONSTRAINT UK_KTC_NormalizedKeyword        UNIQUE      (NormalizedKeyword)
);
GO

-- ── AUTO_SYNC_KEYWORD ────────────────────────────────────────
-- Tu khoa duoc cau hinh de tu dong dong bo du lieu dinh ky.
-- Moi keyword co chu ky sync rieng (IntervalMinutes).
CREATE TABLE AUTO_SYNC_KEYWORD (
    KeywordID           UNIQUEIDENTIFIER    NOT NULL  DEFAULT NEWID(),
    Keyword             NVARCHAR(500)       NOT NULL,
    IntervalMinutes     INT                 NOT NULL  DEFAULT 60,
    Enabled             BIT                 NOT NULL  DEFAULT 1,
    LastSyncedAt        DATETIME2(0)        NULL,
    CreatedAt           DATETIME2(0)        NOT NULL  DEFAULT SYSDATETIME(),

    CONSTRAINT PK_AUTO_SYNC_KEYWORD          PRIMARY KEY (KeywordID)
);
GO

-- ── SYSTEM_CONFIG ─────────────────────────────────────────────
-- Cau hinh he thong key-value. Admin quan ly qua UC-24.
CREATE TABLE SYSTEM_CONFIG (
    ConfigID    UNIQUEIDENTIFIER    NOT NULL  DEFAULT NEWID(),
    ConfigKey   NVARCHAR(100)       NOT NULL,
    ConfigValue NVARCHAR(500)       NOT NULL,
    Description NVARCHAR(500)       NULL,
    UpdatedAt   DATETIME2(0)        NOT NULL  DEFAULT SYSDATETIME(),
    UpdatedBy   UNIQUEIDENTIFIER    NULL,

    CONSTRAINT PK_SYSTEM_CONFIG     PRIMARY KEY (ConfigID),
    CONSTRAINT UK_CONFIG_Key        UNIQUE      (ConfigKey),
    CONSTRAINT FK_CONFIG_UpdatedBy  FOREIGN KEY (UpdatedBy) REFERENCES [USER](UserID)
);
GO

-- ── USER_USAGE ────────────────────────────────────────────────
-- Theo doi so lan su dung hang thang cua Academic User (freemium).
-- Scheduler reset Count ve 0 vao dau moi thang.
-- SearchCount >= limit -> chan va nhac nang cap len Researcher.
CREATE TABLE USER_USAGE (
    UsageID         UNIQUEIDENTIFIER    NOT NULL  DEFAULT NEWID(),
    UserID          UNIQUEIDENTIFIER    NOT NULL,
    UsageMonth      CHAR(7)             NOT NULL,           -- 'YYYY-MM' vd: '2026-05'
    SearchCount     INT                 NOT NULL  DEFAULT 0,
    ViewCount       INT                 NOT NULL  DEFAULT 0,
    ChartViewCount  INT                 NOT NULL  DEFAULT 0,
    LastUpdated     DATETIME2(0)        NOT NULL  DEFAULT SYSDATETIME(),

    CONSTRAINT PK_USER_USAGE        PRIMARY KEY (UsageID),
    CONSTRAINT UK_USAGE_UserMonth   UNIQUE      (UserID, UsageMonth),
    CONSTRAINT FK_USAGE_User        FOREIGN KEY (UserID) REFERENCES [USER](UserID)
                                    ON DELETE CASCADE,
    CONSTRAINT CK_USAGE_Month       CHECK (UsageMonth LIKE '[0-9][0-9][0-9][0-9]-[0-1][0-9]')
);
GO

-- ── AUDIT_LOG ────────────────────────────────────────────────
-- Lich su hanh dong cua Admin (UC-25).
CREATE TABLE AUDIT_LOG (
    AuditID     UNIQUEIDENTIFIER    NOT NULL  DEFAULT NEWID(),
    AdminID     UNIQUEIDENTIFIER    NOT NULL,
    Action      NVARCHAR(200)       NOT NULL,               -- 'LOCK_USER' | 'UPDATE_CONFIG' | ...
    TargetTable NVARCHAR(100)       NULL,
    TargetID    NVARCHAR(100)       NULL,
    OldValue    NVARCHAR(MAX)       NULL,                   -- JSON
    NewValue    NVARCHAR(MAX)       NULL,                   -- JSON
    IPAddress   NVARCHAR(45)        NULL,
    CreatedAt   DATETIME2(0)        NOT NULL  DEFAULT SYSDATETIME(),

    CONSTRAINT PK_AUDIT_LOG     PRIMARY KEY (AuditID),
    CONSTRAINT FK_AUDIT_Admin   FOREIGN KEY (AdminID) REFERENCES [USER](UserID)
);
GO

-- ============================================================
--  INDEXES
-- ============================================================

-- USER
CREATE INDEX IX_USER_RoleID         ON [USER](RoleID);
CREATE INDEX IX_USER_IsActive       ON [USER](IsActive);

-- USER_SESSION
CREATE INDEX IX_SESSION_UserID      ON USER_SESSION(UserID);
CREATE INDEX IX_SESSION_ExpiresAt   ON USER_SESSION(ExpiresAt);
CREATE INDEX IX_SESSION_RefreshTokenHash ON USER_SESSION(RefreshTokenHash);

-- VERIFICATION_TOKEN
CREATE INDEX IX_VT_UserID           ON VERIFICATION_TOKEN(UserID);
CREATE INDEX IX_VT_Token            ON VERIFICATION_TOKEN(Token);
CREATE INDEX IX_VT_ExpiresAt        ON VERIFICATION_TOKEN(ExpiresAt);

-- SYNC_LOG
CREATE INDEX IX_SYNC_SourceID       ON SYNC_LOG(SourceID);
CREATE INDEX IX_SYNC_StartedAt      ON SYNC_LOG(StartedAt DESC);

-- RESEARCH_FIELD
CREATE INDEX IX_FIELD_ParentID      ON RESEARCH_FIELD(ParentFieldID);

-- JOURNAL
CREATE INDEX IX_JOURNAL_FieldID     ON JOURNAL(FieldID);
CREATE INDEX IX_JOURNAL_SourceID    ON JOURNAL(SourceID);
-- Filtered unique: chi enforce unique tren ISSN khac NULL
CREATE UNIQUE INDEX UK_JOURNAL_ISSN ON JOURNAL(ISSN) WHERE ISSN IS NOT NULL;

-- AUTHOR
-- Filtered unique: chi enforce unique khi ExternalAuthorID khac NULL
CREATE UNIQUE INDEX UK_AUTHOR_External ON AUTHOR(SourceID, ExternalAuthorID) WHERE ExternalAuthorID IS NOT NULL;
CREATE INDEX IX_AUTHOR_Country ON AUTHOR(Country);

-- KEYWORD
CREATE INDEX IX_KEYWORD_FieldID     ON KEYWORD(FieldID);
CREATE INDEX IX_KEYWORD_Normalized  ON KEYWORD(NormalizedText);

-- RESEARCH_PAPER
CREATE INDEX IX_PAPER_JournalID     ON RESEARCH_PAPER(JournalID);
CREATE INDEX IX_PAPER_FieldID       ON RESEARCH_PAPER(FieldID);
CREATE INDEX IX_PAPER_PubYear       ON RESEARCH_PAPER(PubYear DESC);
CREATE INDEX IX_PAPER_CreatedAt     ON RESEARCH_PAPER(CreatedAt DESC);
CREATE INDEX IX_PAPER_CitationCount ON RESEARCH_PAPER(CitationCount DESC);
-- Filtered unique: chi enforce unique tren DOI khac NULL
CREATE UNIQUE INDEX UK_PAPER_DOI ON RESEARCH_PAPER(DOI) WHERE DOI IS NOT NULL;

-- Full-text search tren Title va Abstract
-- (can bat Full-Text Search feature tren SQL Server)
-- CREATE FULLTEXT CATALOG FT_JournalTrend AS DEFAULT;
-- CREATE FULLTEXT INDEX ON RESEARCH_PAPER(Title, Abstract)
--     KEY INDEX PK_RESEARCH_PAPER ON FT_JournalTrend;

-- RESEARCH_TOPIC
CREATE INDEX IX_TOPIC_FieldID       ON RESEARCH_TOPIC(FieldID);
CREATE INDEX IX_TOPIC_TrendScore    ON RESEARCH_TOPIC(TrendScore DESC);
CREATE INDEX IX_TOPIC_IsTrending    ON RESEARCH_TOPIC(IsTrending);

-- PUBLICATION_TREND
CREATE INDEX IX_TREND_Target        ON PUBLICATION_TREND(TrendTarget, TargetID);
CREATE INDEX IX_TREND_Period        ON PUBLICATION_TREND(PeriodType, PeriodValue);
CREATE INDEX IX_TREND_Full          ON PUBLICATION_TREND(TrendTarget, TargetID, PeriodType, PeriodValue);

-- BOOKMARK
CREATE INDEX IX_BOOKMARK_UserID     ON BOOKMARK(UserID);
-- Filtered unique: chi enforce unique khi target khac NULL (SQL Server UNIQUE constraint chi cho phep 1 NULL)
CREATE UNIQUE INDEX UK_BOOKMARK_Paper   ON BOOKMARK(UserID, PaperID)   WHERE PaperID   IS NOT NULL;
CREATE UNIQUE INDEX UK_BOOKMARK_Keyword ON BOOKMARK(UserID, KeywordID) WHERE KeywordID IS NOT NULL;

-- FOLLOW
CREATE INDEX IX_FOLLOW_UserID       ON FOLLOW(UserID);
-- Filtered unique: chi enforce unique khi target khac NULL
CREATE UNIQUE INDEX UK_FOLLOW_Journal   ON FOLLOW(UserID, JournalID) WHERE JournalID IS NOT NULL;
CREATE UNIQUE INDEX UK_FOLLOW_Topic     ON FOLLOW(UserID, TopicID)   WHERE TopicID   IS NOT NULL;
CREATE UNIQUE INDEX UK_FOLLOW_Keyword   ON FOLLOW(UserID, KeywordID) WHERE KeywordID IS NOT NULL;

-- NOTIFICATION
CREATE INDEX IX_NOTIF_UserUnread    ON NOTIFICATION(UserID, IsRead, CreatedAt DESC);
CREATE INDEX IX_NOTIF_CreatedAt     ON NOTIFICATION(CreatedAt DESC);

-- PDF_REQUEST
CREATE INDEX IX_PDF_REQUEST_StatusRequestedAt
    ON PDF_REQUEST(Status, RequestedAt DESC);
CREATE UNIQUE INDEX UX_PDF_REQUEST_UserPaperPending
    ON PDF_REQUEST(UserID, PaperID)
    WHERE Status = 'pending';

-- REPORT
CREATE INDEX IX_REPORT_UserID       ON REPORT(UserID);
CREATE INDEX IX_REPORT_Status       ON REPORT(Status);

-- USER_USAGE
CREATE INDEX IX_USAGE_UserMonth     ON USER_USAGE(UserID, UsageMonth);

-- AUDIT_LOG
CREATE INDEX IX_AUDIT_AdminID       ON AUDIT_LOG(AdminID);
CREATE INDEX IX_AUDIT_CreatedAt     ON AUDIT_LOG(CreatedAt DESC);

-- BOOKMARK_COLLECTION
CREATE INDEX IX_BC_UserID           ON BOOKMARK_COLLECTION(UserID);
CREATE UNIQUE INDEX UK_BC_User_Name ON BOOKMARK_COLLECTION(UserID, Name);

-- BOOKMARK (bo sung)
CREATE INDEX IX_BOOKMARK_CollectionID ON BOOKMARK(CollectionID);
CREATE UNIQUE INDEX UK_BOOKMARK_Collection ON BOOKMARK(UserID, CollectionID) WHERE CollectionID IS NOT NULL;

-- FOLLOW (bo sung)
CREATE UNIQUE INDEX UK_FOLLOW_Author ON FOLLOW(UserID, AuthorID) WHERE AuthorID IS NOT NULL;

-- USER_READING_HISTORY
CREATE INDEX IX_URH_UserID          ON USER_READING_HISTORY(UserID);
CREATE INDEX IX_URH_PaperID         ON USER_READING_HISTORY(PaperID);
CREATE INDEX IX_URH_ViewedAt        ON USER_READING_HISTORY(ViewedAt DESC);

-- USER_SEARCH_HISTORY
CREATE INDEX IX_USH_UserID          ON USER_SEARCH_HISTORY(UserID);
CREATE INDEX IX_USH_SearchedAt      ON USER_SEARCH_HISTORY(SearchedAt DESC);

-- SEARCH_KEYWORD
CREATE INDEX IX_SK_SearchCount      ON SEARCH_KEYWORD(SearchCount DESC);

-- TRENDING_TOPIC
CREATE INDEX IX_TT_DisplayOrder     ON TRENDING_TOPIC(DisplayOrder);
CREATE INDEX IX_TT_UpdatedAt        ON TRENDING_TOPIC(UpdatedAt DESC);

-- AUTO_SYNC_KEYWORD
CREATE INDEX IX_ASK_Enabled         ON AUTO_SYNC_KEYWORD(Enabled);
CREATE INDEX IX_ASK_LastSyncedAt    ON AUTO_SYNC_KEYWORD(LastSyncedAt);

-- KEYWORD_TREND_CACHE
CREATE INDEX IX_KTC_NormalizedKeyword ON KEYWORD_TREND_CACHE(NormalizedKeyword);
CREATE INDEX IX_KTC_UpdatedAt         ON KEYWORD_TREND_CACHE(UpdatedAt DESC);

GO

-- ============================================================
--  DU LIEU MAU (Seed Data)
-- ============================================================

-- ── 3 Role co dinh ───────────────────────────────────────────
INSERT INTO ROLE (RoleID, RoleName, Description) VALUES
    ('A0000001-0000-0000-0000-000000000001',
     'admin',
     N'Quan tri vien he thong. Quan ly tai khoan, cau hinh API va theo doi hoat dong he thong.'),
    ('A0000001-0000-0000-0000-000000000002',
     'researcher',
     N'Nha nghien cuu (Paid). Toan quyen truy cap tat ca chuc nang phan tich va bao cao.'),
    ('A0000001-0000-0000-0000-000000000003',
     'academic_user',
     N'Nguoi dung hoc thuat (Free tier). Bao gom giang vien va sinh vien. Gioi han luot su dung/thang.');
GO

-- ── 3 Nguon API hoc thuat ─────────────────────────────────────
INSERT INTO API_SOURCE (SourceID, SourceName, BaseURL, IsActive, RateLimitRPM) VALUES
    ('B0000002-0000-0000-0000-000000000001',
     'semantic_scholar',
     'https://api.semanticscholar.org/graph/v1', 1, 100),
    ('B0000002-0000-0000-0000-000000000002',
     'openalex',
     'https://api.openalex.org', 1, 60),
    ('B0000002-0000-0000-0000-000000000003',
     'crossref',
     'https://api.crossref.org', 1, 50);
GO

-- ── Cau hinh he thong mac dinh ────────────────────────────────
INSERT INTO SYSTEM_CONFIG (ConfigKey, ConfigValue, Description) VALUES
    ('academic_monthly_search_limit',  '30',
     N'So lan tim kiem toi da moi thang cho Academic User'),
    ('academic_monthly_view_limit',    '20',
     N'So lan xem chi tiet bai bao toi da moi thang cho Academic User'),
    ('academic_monthly_chart_limit',   '15',
     N'So lan xem bieu do xu huong toi da moi thang cho Academic User'),
    ('academic_max_bookmarks',         '50',
     N'So bookmark toi da cho Academic User'),
    ('academic_max_follows',           '20',
     N'So follow toi da cho Academic User'),
    ('sync_interval_hours',            '24',
     N'Chu ky tu dong dong bo du lieu (gio)'),
    ('top_keywords_dashboard',         '20',
     N'So keyword hien thi tren dashboard xu huong'),
    ('trend_score_threshold',          '0.7',
     N'Nguong TrendScore de danh dau topic la IsTrending = 1'),
    ('trend_alert_growth_threshold',   '50',
     N'% tang truong toi thieu de gui canh bao xu huong (Researcher)');
GO

-- ── Linh vuc nghien cuu mac dinh ─────────────────────────────
DECLARE @CS   UNIQUEIDENTIFIER = NEWID();
DECLARE @AI   UNIQUEIDENTIFIER = NEWID();
DECLARE @ML   UNIQUEIDENTIFIER = NEWID();
DECLARE @NLP  UNIQUEIDENTIFIER = NEWID();
DECLARE @CV   UNIQUEIDENTIFIER = NEWID();
DECLARE @SE   UNIQUEIDENTIFIER = NEWID();
DECLARE @DS   UNIQUEIDENTIFIER = NEWID();
DECLARE @Sec  UNIQUEIDENTIFIER = NEWID();

INSERT INTO RESEARCH_FIELD (FieldID, ParentFieldID, FieldName, IsTracked, Description) VALUES
    (@CS,   NULL, 'Computer Science',           1, N'Khoa hoc may tinh tong quat'),
    (@AI,   @CS,  'Artificial Intelligence',    1, N'Tri tue nhan tao'),
    (@ML,   @AI,  'Machine Learning',           1, N'Hoc may'),
    (@NLP,  @AI,  'Natural Language Processing',1, N'Xu ly ngon ngu tu nhien'),
    (@CV,   @AI,  'Computer Vision',            1, N'Thi giac may tinh'),
    (@SE,   @CS,  'Software Engineering',       1, N'Ky thuat phan mem'),
    (@DS,   @CS,  'Data Science',               1, N'Khoa hoc du lieu'),
    (@Sec,  @CS,  'Cybersecurity',              1, N'An toan thong tin');
GO

-- ── Tai khoan Admin mac dinh ──────────────────────────────────
-- LUU Y: Thay PasswordHash bang gia tri bcrypt thuc te truoc khi deploy!
INSERT INTO [USER] (UserID, RoleID, Email, PasswordHash, FullName, IsActive) VALUES
    ('C0000003-0000-0000-0000-000000000001',
     'A0000001-0000-0000-0000-000000000001',
     'admin@journaltrend.edu.vn',
     '$2b$12$REPLACE_WITH_REAL_BCRYPT_HASH',
     N'System Administrator',
     1);
GO

-- ============================================================
--  VIEWS
-- ============================================================

-- View: Thong tin day du user kem role name
CREATE OR ALTER VIEW V_USER_DETAIL AS
SELECT
    u.UserID,
    u.Email,
    u.FullName,
    u.Institution,
    u.IsActive,
    u.CreatedAt,
    u.LastLoginAt,
    r.RoleName,
    r.RoleID
FROM [USER] u
JOIN ROLE r ON u.RoleID = r.RoleID;
GO

-- View: Top trending topics hien tai
CREATE OR ALTER VIEW V_TRENDING_TOPICS AS
SELECT
    t.TopicID,
    t.TopicName,
    t.TrendScore,
    t.PaperCount,
    t.UpdatedAt,
    f.FieldName,
    COUNT(tk.KeywordID) AS KeywordCount
FROM RESEARCH_TOPIC t
LEFT JOIN RESEARCH_FIELD f  ON t.FieldID = f.FieldID
LEFT JOIN TOPIC_KEYWORD  tk ON t.TopicID = tk.TopicID
WHERE t.IsTrending = 1
GROUP BY
    t.TopicID, t.TopicName, t.TrendScore,
    t.PaperCount, t.UpdatedAt, f.FieldName;
GO

-- View: Usage cua Academic User thang hien tai
CREATE OR ALTER VIEW V_ACADEMIC_USAGE_CURRENT AS
SELECT
    u.UserID,
    u.Email,
    u.FullName,
    uu.UsageMonth,
    ISNULL(uu.SearchCount,    0) AS SearchCount,
    ISNULL(uu.ViewCount,      0) AS ViewCount,
    ISNULL(uu.ChartViewCount, 0) AS ChartViewCount,
    (SELECT ConfigValue FROM SYSTEM_CONFIG
     WHERE ConfigKey = 'academic_monthly_search_limit') AS SearchLimit,
    (SELECT ConfigValue FROM SYSTEM_CONFIG
     WHERE ConfigKey = 'academic_monthly_view_limit')   AS ViewLimit,
    (SELECT ConfigValue FROM SYSTEM_CONFIG
     WHERE ConfigKey = 'academic_monthly_chart_limit')  AS ChartLimit
FROM [USER] u
JOIN ROLE r ON u.RoleID = r.RoleID AND r.RoleName = 'academic_user'
LEFT JOIN USER_USAGE uu
    ON u.UserID = uu.UserID
    AND uu.UsageMonth = FORMAT(SYSDATETIME(), 'yyyy-MM');
GO

-- View: Lich su sync kem ten nguon
CREATE OR ALTER VIEW V_SYNC_HISTORY AS
SELECT
    sl.LogID,
    sl.StartedAt,
    sl.CompletedAt,
    sl.SyncType,
    sl.IsManual,
    sl.Status,
    sl.PapersFetched,
    sl.PapersInserted,
    sl.ErrorMessage,
    src.SourceName,
    DATEDIFF(SECOND, sl.StartedAt, sl.CompletedAt) AS DurationSeconds
FROM SYNC_LOG sl
JOIN API_SOURCE src ON sl.SourceID = src.SourceID;
GO

-- ============================================================
--  STORED PROCEDURES
-- ============================================================

-- SP: Kiem tra va tang usage count cho Academic User
-- Tra ve 1 neu con trong gioi han, 0 neu da vuot
CREATE OR ALTER PROCEDURE SP_CHECK_AND_INCREMENT_USAGE
    @UserID     UNIQUEIDENTIFIER,
    @UsageType  NVARCHAR(20),       -- 'search' | 'view' | 'chart'
    @CanProceed BIT OUTPUT
AS
BEGIN
    SET NOCOUNT ON;

    DECLARE @Month      CHAR(7) = FORMAT(SYSDATETIME(), 'yyyy-MM');
    DECLARE @Current    INT     = 0;
    DECLARE @Limit      INT     = 999999;
    DECLARE @ConfigKey  NVARCHAR(100);

    SET @ConfigKey = CASE @UsageType
        WHEN 'search' THEN 'academic_monthly_search_limit'
        WHEN 'view'   THEN 'academic_monthly_view_limit'
        WHEN 'chart'  THEN 'academic_monthly_chart_limit'
        ELSE NULL
    END;

    IF @ConfigKey IS NULL
    BEGIN
        SET @CanProceed = 0;
        RETURN;
    END;

    -- Lay gioi han tu config
    SELECT @Limit = TRY_CAST(ConfigValue AS INT)
    FROM SYSTEM_CONFIG WHERE ConfigKey = @ConfigKey;

    -- Upsert record usage thang hien tai
    MERGE USER_USAGE AS target
    USING (SELECT @UserID AS UserID, @Month AS UsageMonth) AS src
    ON target.UserID = src.UserID AND target.UsageMonth = src.UsageMonth
    WHEN NOT MATCHED THEN
        INSERT (UserID, UsageMonth) VALUES (@UserID, @Month);

    -- Lay so hien tai
    SELECT @Current = CASE @UsageType
        WHEN 'search' THEN SearchCount
        WHEN 'view'   THEN ViewCount
        WHEN 'chart'  THEN ChartViewCount
    END
    FROM USER_USAGE
    WHERE UserID = @UserID AND UsageMonth = @Month;

    -- Kiem tra gioi han
    IF @Current >= @Limit
    BEGIN
        SET @CanProceed = 0;

        -- Tu dong tao thong bao upgrade_prompt neu chua co trong ngay hom nay
        IF NOT EXISTS (
            SELECT 1 FROM NOTIFICATION
            WHERE UserID = @UserID
              AND [Type] = 'upgrade_prompt'
              AND CAST(CreatedAt AS DATE) = CAST(SYSDATETIME() AS DATE)
        )
        INSERT INTO NOTIFICATION (UserID, [Type], Title, [Message])
        VALUES (
            @UserID,
            'upgrade_prompt',
            N'Ban da het luot su dung thang nay',
            N'Nang cap len Researcher de su dung khong gioi han va mo khoa tinh nang nang cao.'
        );
        RETURN;
    END;

    -- Tang count
    UPDATE USER_USAGE
    SET
        SearchCount    = CASE WHEN @UsageType = 'search' THEN SearchCount    + 1 ELSE SearchCount    END,
        ViewCount      = CASE WHEN @UsageType = 'view'   THEN ViewCount      + 1 ELSE ViewCount      END,
        ChartViewCount = CASE WHEN @UsageType = 'chart'  THEN ChartViewCount + 1 ELSE ChartViewCount END,
        LastUpdated    = SYSDATETIME()
    WHERE UserID = @UserID AND UsageMonth = @Month;

    -- Gui canh bao khi dat 80%
    IF (@Current + 1) >= (@Limit * 0.8)
       AND NOT EXISTS (
           SELECT 1 FROM NOTIFICATION
           WHERE UserID = @UserID
             AND [Type] = 'upgrade_prompt'
             AND CAST(CreatedAt AS DATE) = CAST(SYSDATETIME() AS DATE)
       )
    BEGIN
        INSERT INTO NOTIFICATION (UserID, [Type], Title, [Message])
        VALUES (
            @UserID,
            'upgrade_prompt',
            N'Ban sap het luot su dung (80%)',
            N'Con lai ' + CAST(@Limit - @Current - 1 AS NVARCHAR) +
            N' luot trong thang nay. Nang cap de dung khong gioi han.'
        );
    END;

    SET @CanProceed = 1;
END;
GO

-- SP: Don dep session het han (chay dinh ky boi Scheduler)
CREATE OR ALTER PROCEDURE SP_CLEANUP_EXPIRED_SESSIONS
AS
BEGIN
    SET NOCOUNT ON;
    DELETE FROM USER_SESSION
    WHERE COALESCE(RefreshExpiresAt, ExpiresAt) < SYSDATETIME();
    SELECT @@ROWCOUNT AS [DeletedSessions];
END;
GO

-- SP: Reset usage hang thang cho tat ca Academic User
-- Chay vao 00:00 ngay 1 hang thang
CREATE OR ALTER PROCEDURE SP_RESET_MONTHLY_USAGE
AS
BEGIN
    SET NOCOUNT ON;

    -- Khong xoa record cu, chi insert record moi cho thang moi
    -- Record cu giu lai de thong ke lich su su dung
    DECLARE @ThisMonth CHAR(7) = FORMAT(SYSDATETIME(), 'yyyy-MM');

    INSERT INTO USER_USAGE (UserID, UsageMonth, SearchCount, ViewCount, ChartViewCount)
    SELECT u.UserID, @ThisMonth, 0, 0, 0
    FROM [USER] u
    JOIN ROLE r ON u.RoleID = r.RoleID AND r.RoleName = 'academic_user'
    WHERE u.IsActive = 1
      AND NOT EXISTS (
          SELECT 1 FROM USER_USAGE
          WHERE UserID = u.UserID AND UsageMonth = @ThisMonth
      );

    SELECT @@ROWCOUNT AS [InitializedUsers];
END;
GO

-- SP: Cap nhat TrendScore va IsTrending (chay sau moi sync)
CREATE OR ALTER PROCEDURE SP_REFRESH_TOPIC_TRENDS
AS
BEGIN
    SET NOCOUNT ON;

    DECLARE @Threshold DECIMAL(10,4) = 0.7;
    SELECT @Threshold = TRY_CAST(ConfigValue AS DECIMAL(10,4))
    FROM SYSTEM_CONFIG WHERE ConfigKey = 'trend_score_threshold';

    -- Cap nhat PaperCount
    UPDATE rt
    SET rt.PaperCount = (
        SELECT COUNT(DISTINCT pk.PaperID)
        FROM TOPIC_KEYWORD tk
        JOIN PAPER_KEYWORD pk ON tk.KeywordID = pk.KeywordID
        WHERE tk.TopicID = rt.TopicID
    ),
    rt.UpdatedAt = SYSDATETIME()
    FROM RESEARCH_TOPIC rt;

    -- Tinh TrendScore trung binh GrowthRate cac thang gan day
    UPDATE rt
    SET rt.TrendScore = ISNULL((
        SELECT AVG(pt.GrowthRate)
        FROM PUBLICATION_TREND pt
        WHERE pt.TrendTarget = 'topic'
          AND pt.TargetID    = rt.TopicID
          AND pt.PeriodType  = 'monthly'
          AND pt.GrowthRate  IS NOT NULL
    ), 0)
    FROM RESEARCH_TOPIC rt;

    -- Danh dau IsTrending
    UPDATE RESEARCH_TOPIC
    SET IsTrending = CASE WHEN TrendScore >= @Threshold THEN 1 ELSE 0 END;

    SELECT @@ROWCOUNT AS [UpdatedTopics];
END;
GO

-- =============================================
-- SCITRACK — Idea Feature (v2.0)
-- IDEA_ANALYSIS + PAPER_EVALUATION_CACHE
-- =============================================

IF NOT EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.TABLES
    WHERE TABLE_SCHEMA = 'dbo' AND TABLE_NAME = 'IDEA_ANALYSIS'
)
BEGIN
    CREATE TABLE [dbo].[IDEA_ANALYSIS] (
        [analysis_id] uniqueidentifier
            CONSTRAINT [DF_IDEA_ANALYSIS_id] DEFAULT (newid()) NOT NULL,
        [user_id] uniqueidentifier NOT NULL,
        [idea_text] nvarchar(max) NOT NULL,
        [idea_hash] varchar(64) NOT NULL,
        [keywords] nvarchar(max) NULL,
        [result_json] nvarchar(max) NOT NULL,
        [paper_count] int
            CONSTRAINT [DF_IDEA_ANALYSIS_paper_count] DEFAULT ((0)) NULL,
        [created_at] datetime2(0)
            CONSTRAINT [DF_IDEA_ANALYSIS_created_at] DEFAULT (sysdatetime()) NOT NULL,
        [updated_at] datetime2(0) NULL,

        CONSTRAINT [PK_IDEA_ANALYSIS]
            PRIMARY KEY CLUSTERED ([analysis_id] ASC),
        CONSTRAINT [FK_IDEA_ANALYSIS_USER]
            FOREIGN KEY ([user_id]) REFERENCES [dbo].[USER]([UserID])
    );
    CREATE INDEX [IX_IDEA_ANALYSIS_USER_ID] ON [dbo].[IDEA_ANALYSIS]([user_id] ASC);
    CREATE INDEX [IX_IDEA_ANALYSIS_CREATED_AT] ON [dbo].[IDEA_ANALYSIS]([created_at] DESC);
    CREATE INDEX [IX_IDEA_ANALYSIS_IDEA_HASH] ON [dbo].[IDEA_ANALYSIS]([idea_hash] ASC);
    PRINT '✓ Created table IDEA_ANALYSIS';
END
ELSE
    PRINT '→ Table IDEA_ANALYSIS already exists — skipped';
GO

IF NOT EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.TABLES
    WHERE TABLE_SCHEMA = 'dbo' AND TABLE_NAME = 'PAPER_EVALUATION_CACHE'
)
BEGIN
    CREATE TABLE [dbo].[PAPER_EVALUATION_CACHE] (
        [cache_id] uniqueidentifier
            CONSTRAINT [DF_PAPER_EVAL_CACHE_id] DEFAULT (newid()) NOT NULL,
        [paper_id] uniqueidentifier NOT NULL,
        [idea_hash] varchar(64) NOT NULL,
        [criteria_json] nvarchar(max) NOT NULL,
        [created_at] datetime2(0)
            CONSTRAINT [DF_PAPER_EVAL_CACHE_created_at] DEFAULT (sysdatetime()) NOT NULL,

        CONSTRAINT [PK_PAPER_EVALUATION_CACHE]
            PRIMARY KEY CLUSTERED ([cache_id] ASC),
        CONSTRAINT [UQ_PAPER_IDEA]
            UNIQUE NONCLUSTERED ([paper_id] ASC, [idea_hash] ASC)
    );
    CREATE INDEX [IX_PAPER_EVAL_CACHE_IDEA_HASH] ON [dbo].[PAPER_EVALUATION_CACHE]([idea_hash] ASC);
    CREATE INDEX [IX_PAPER_EVAL_CACHE_CREATED_AT] ON [dbo].[PAPER_EVALUATION_CACHE]([created_at] ASC);
    PRINT '✓ Created table PAPER_EVALUATION_CACHE';
END
ELSE
    PRINT '→ Table PAPER_EVALUATION_CACHE already exists — skipped';
GO


CREATE TABLE ROLE_UPGRADE_REQUEST (
    RequestID       UNIQUEIDENTIFIER    NOT NULL  DEFAULT NEWID(),
    UserID          UNIQUEIDENTIFIER    NOT NULL,
    FullName        NVARCHAR(200)       NOT NULL,
    Institution     NVARCHAR(300)       NOT NULL,
    ResearchField   NVARCHAR(200)       NOT NULL,
    [Position]      NVARCHAR(100)       NOT NULL,
    Orcid           NVARCHAR(50)        NULL,
    Reason          NVARCHAR(MAX)       NOT NULL,
    Status          NVARCHAR(20)        NOT NULL  DEFAULT 'PENDING'
                        CHECK (Status IN ('PENDING','APPROVED','REJECTED')),
    AdminNote       NVARCHAR(500)       NULL,
    ReviewedBy      UNIQUEIDENTIFIER    NULL,
    ReviewedAt      DATETIME2(0)        NULL,
    CreatedAt       DATETIME2(0)        NOT NULL  DEFAULT SYSDATETIME(),

    CONSTRAINT PK_ROLE_UPGRADE_REQUEST PRIMARY KEY (RequestID),
    CONSTRAINT FK_UPGRADE_REQ_User     FOREIGN KEY (UserID)     REFERENCES [USER](UserID),
    CONSTRAINT FK_UPGRADE_REQ_Admin    FOREIGN KEY (ReviewedBy) REFERENCES [USER](UserID)
);
CREATE INDEX IX_UPGRADE_REQ_UserID   ON ROLE_UPGRADE_REQUEST(UserID);
CREATE INDEX IX_UPGRADE_REQ_Status   ON ROLE_UPGRADE_REQUEST(Status);
CREATE INDEX IX_UPGRADE_REQ_CreatedAt ON ROLE_UPGRADE_REQUEST(CreatedAt DESC);