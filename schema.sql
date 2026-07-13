-- ============================================================
--  JournalTrendDB schema-only snapshot
--  Source: AWS SQL Server RDS
--  Generated: 2026-07-13 22:15:33 +07:00
--  Data rows are intentionally excluded.
-- ============================================================

CREATE DATABASE [JournalTrendDB];
GO

USE [JournalTrendDB];
GO

CREATE TABLE [dbo].[API_SOURCE] (
    [SourceID] uniqueidentifier CONSTRAINT [DF__API_SOURC__Sourc__619B8048] DEFAULT (newid()) NOT NULL,
    [SourceName] nvarchar(100) NOT NULL,
    [BaseURL] nvarchar(500) NOT NULL,
    [IsActive] bit CONSTRAINT [DF__API_SOURC__IsAct__628FA481] DEFAULT ((1)) NOT NULL,
    [RateLimitRPM] int NULL,
    [LastSyncedAt] datetime2(0) NULL
);
GO

CREATE TABLE [dbo].[AUDIT_LOG] (
    [AuditID] uniqueidentifier CONSTRAINT [DF__AUDIT_LOG__Audit__74794A92] DEFAULT (newid()) NOT NULL,
    [AdminID] uniqueidentifier NOT NULL,
    [Action] nvarchar(200) NOT NULL,
    [TargetTable] nvarchar(100) NULL,
    [TargetID] nvarchar(100) NULL,
    [OldValue] nvarchar(MAX) NULL,
    [NewValue] nvarchar(MAX) NULL,
    [IPAddress] nvarchar(45) NULL,
    [CreatedAt] datetime2(0) CONSTRAINT [DF__AUDIT_LOG__Creat__756D6ECB] DEFAULT (sysdatetime()) NOT NULL
);
GO

CREATE TABLE [dbo].[AUTO_SYNC_KEYWORD] (
    [KeywordID] uniqueidentifier NOT NULL,
    [Keyword] nvarchar(500) NOT NULL,
    [IntervalMinutes] int CONSTRAINT [DF__AUTO_SYNC__Inter__0880433F] DEFAULT ((60)) NOT NULL,
    [Enabled] bit CONSTRAINT [DF__AUTO_SYNC__Enabl__09746778] DEFAULT ((1)) NOT NULL,
    [LastSyncedAt] datetime2(7) NULL,
    [CreatedAt] datetime2(7) CONSTRAINT [DF__AUTO_SYNC__Creat__0A688BB1] DEFAULT (getdate()) NOT NULL
);
GO

CREATE TABLE [dbo].[AUTHOR] (
    [AuthorID] uniqueidentifier CONSTRAINT [DF__AUTHOR__AuthorID__7C4F7684] DEFAULT (newid()) NOT NULL,
    [SourceID] uniqueidentifier NOT NULL,
    [ExternalAuthorID] nvarchar(200) NULL,
    [FullName] nvarchar(300) NOT NULL,
    [Affiliation] nvarchar(500) NULL,
    [HIndex] int CONSTRAINT [DF__AUTHOR__HIndex__7D439ABD] DEFAULT ((0)) NULL,
    [TotalCitations] int CONSTRAINT [DF__AUTHOR__TotalCit__7E37BEF6] DEFAULT ((0)) NULL,
    [Country] nvarchar(100) NULL,
    [I10Index] int CONSTRAINT [DF__AUTHOR__I10Index__2CBDA3B5] DEFAULT ((0)) NOT NULL,
    [WorksCount] int CONSTRAINT [DF__AUTHOR__WorksCou__2DB1C7EE] DEFAULT ((0)) NOT NULL
);
GO

CREATE TABLE [dbo].[BOOKMARK] (
    [BookmarkID] uniqueidentifier CONSTRAINT [DF__BOOKMARK__Bookma__32AB8735] DEFAULT (newid()) NOT NULL,
    [UserID] uniqueidentifier NOT NULL,
    [PaperID] uniqueidentifier NULL,
    [KeywordID] uniqueidentifier NULL,
    [Notes] nvarchar(500) NULL,
    [CreatedAt] datetime2(0) CONSTRAINT [DF__BOOKMARK__Create__339FAB6E] DEFAULT (sysdatetime()) NOT NULL,
    [CollectionID] uniqueidentifier NULL,
    [LastNotifiedMilestone] int CONSTRAINT [DF__BOOKMARK__LastNo__2704CA5F] DEFAULT ((0)) NOT NULL
);
GO

CREATE TABLE [dbo].[BOOKMARK_COLLECTION] (
    [CollectionID] uniqueidentifier CONSTRAINT [DF__BOOKMARK___Colle__0E391C95] DEFAULT (newid()) NOT NULL,
    [UserID] uniqueidentifier NOT NULL,
    [Name] nvarchar(200) NOT NULL,
    [Description] nvarchar(500) NULL,
    [CreatedAt] datetime2(0) CONSTRAINT [DF__BOOKMARK___Creat__0F2D40CE] DEFAULT (sysdatetime()) NOT NULL,
    [UpdatedAt] datetime2(0) CONSTRAINT [DF__BOOKMARK___Updat__10216507] DEFAULT (sysdatetime()) NOT NULL,
    [LastNotifiedMilestone] int CONSTRAINT [DF__BOOKMARK___LastN__28ED12D1] DEFAULT ((0)) NOT NULL
);
GO

CREATE TABLE [dbo].[DASHBOARD_WIDGET] (
    [WidgetID] uniqueidentifier CONSTRAINT [DF__DASHBOARD__Widge__58D1301D] DEFAULT (newid()) NOT NULL,
    [UserID] uniqueidentifier NOT NULL,
    [WidgetType] nvarchar(30) NOT NULL,
    [Title] nvarchar(200) NOT NULL,
    [Config] nvarchar(MAX) NULL,
    [PositionX] int CONSTRAINT [DF__DASHBOARD__Posit__5AB9788F] DEFAULT ((0)) NOT NULL,
    [PositionY] int CONSTRAINT [DF__DASHBOARD__Posit__5BAD9CC8] DEFAULT ((0)) NOT NULL,
    [Width] int CONSTRAINT [DF__DASHBOARD__Width__5CA1C101] DEFAULT ((4)) NOT NULL,
    [Height] int CONSTRAINT [DF__DASHBOARD__Heigh__5D95E53A] DEFAULT ((3)) NOT NULL,
    [CreatedAt] datetime2(0) CONSTRAINT [DF__DASHBOARD__Creat__5E8A0973] DEFAULT (sysdatetime()) NOT NULL,
    [UpdatedAt] datetime2(0) CONSTRAINT [DF__DASHBOARD__Updat__5F7E2DAC] DEFAULT (sysdatetime()) NOT NULL
);
GO

CREATE TABLE [dbo].[FOLLOW] (
    [FollowID] uniqueidentifier CONSTRAINT [DF__FOLLOW__FollowID__3A4CA8FD] DEFAULT (newid()) NOT NULL,
    [UserID] uniqueidentifier NOT NULL,
    [JournalID] uniqueidentifier NULL,
    [TopicID] uniqueidentifier NULL,
    [KeywordID] uniqueidentifier NULL,
    [NotifyEnabled] bit CONSTRAINT [DF__FOLLOW__NotifyEn__3B40CD36] DEFAULT ((1)) NOT NULL,
    [CreatedAt] datetime2(0) CONSTRAINT [DF__FOLLOW__CreatedA__3C34F16F] DEFAULT (sysdatetime()) NOT NULL,
    [AuthorID] uniqueidentifier NULL
);
GO

CREATE TABLE [dbo].[JOURNAL] (
    [JournalID] uniqueidentifier CONSTRAINT [DF__JOURNAL__Journal__75A278F5] DEFAULT (newid()) NOT NULL,
    [SourceID] uniqueidentifier NOT NULL,
    [FieldID] uniqueidentifier NULL,
    [JournalName] nvarchar(500) NOT NULL,
    [ISSN] nvarchar(20) NULL,
    [Publisher] nvarchar(300) NULL,
    [ImpactFactor] decimal(8,3) NULL,
    [Quartile] char(2) NULL,
    [IsActive] bit CONSTRAINT [DF__JOURNAL__IsActiv__778AC167] DEFAULT ((1)) NOT NULL
);
GO

CREATE TABLE [dbo].[KEYWORD] (
    [KeywordID] uniqueidentifier CONSTRAINT [DF__KEYWORD__Keyword__02FC7413] DEFAULT (newid()) NOT NULL,
    [FieldID] uniqueidentifier NULL,
    [KeywordText] nvarchar(300) NOT NULL,
    [NormalizedText] nvarchar(300) NOT NULL,
    [PaperCount] int CONSTRAINT [DF__KEYWORD__PaperCo__03F0984C] DEFAULT ((0)) NOT NULL
);
GO

CREATE TABLE [dbo].[NOTIFICATION] (
    [NotifID] uniqueidentifier CONSTRAINT [DF__NOTIFICAT__Notif__43D61337] DEFAULT (newid()) NOT NULL,
    [UserID] uniqueidentifier NOT NULL,
    [Type] nvarchar(20) NOT NULL,
    [Title] nvarchar(300) NOT NULL,
    [Message] nvarchar(MAX) NULL,
    [RelatedPaperID] uniqueidentifier NULL,
    [RelatedJournalID] uniqueidentifier NULL,
    [RelatedTopicID] uniqueidentifier NULL,
    [RelatedKeywordID] uniqueidentifier NULL,
    [IsRead] bit CONSTRAINT [DF__NOTIFICAT__IsRea__45BE5BA9] DEFAULT ((0)) NOT NULL,
    [CreatedAt] datetime2(0) CONSTRAINT [DF__NOTIFICAT__Creat__46B27FE2] DEFAULT (sysdatetime()) NOT NULL
);
GO

CREATE TABLE [dbo].[PAPER_AUTHOR] (
    [PaperID] uniqueidentifier NOT NULL,
    [AuthorID] uniqueidentifier NOT NULL,
    [AuthorOrder] smallint CONSTRAINT [DF__PAPER_AUT__Autho__114A936A] DEFAULT ((1)) NOT NULL,
    [IsCorresponding] bit CONSTRAINT [DF__PAPER_AUT__IsCor__123EB7A3] DEFAULT ((0)) NOT NULL
);
GO

CREATE TABLE [dbo].[PAPER_CACHE] (
    [PaperID] uniqueidentifier NOT NULL,
    [Title] nvarchar(1000) NULL,
    [Abstract] nvarchar(MAX) NULL,
    [Doi] nvarchar(200) NULL,
    [PubYear] smallint NULL,
    [CitationCount] int CONSTRAINT [DF__PAPER_CAC__Citat__55BFB948] DEFAULT ((0)) NULL,
    [JournalName] nvarchar(500) NULL,
    [SourceUrl] nvarchar(500) NULL,
    [OpenAlexWorkId] nvarchar(500) NULL,
    [DataJson] nvarchar(MAX) NULL,
    [UpdatedAt] datetime2(7) CONSTRAINT [DF__PAPER_CAC__Updat__56B3DD81] DEFAULT (sysutcdatetime()) NOT NULL
);
GO

CREATE TABLE [dbo].[PAPER_KEYWORD] (
    [PaperID] uniqueidentifier NOT NULL,
    [KeywordID] uniqueidentifier NOT NULL,
    [RelevanceScore] decimal(5,4) NULL
);
GO

CREATE TABLE [dbo].[PAPER_RATING] (
    [RatingID] uniqueidentifier NOT NULL,
    [UserID] uniqueidentifier NOT NULL,
    [PaperID] uniqueidentifier NOT NULL,
    [Score] int NOT NULL,
    [RatedAt] datetime2(7) NOT NULL
);
GO

CREATE TABLE [dbo].[PDF_REQUEST] (
    [RequestID] uniqueidentifier CONSTRAINT [DF__PDF_REQUE__Reque__2057CCD0] DEFAULT (newid()) NOT NULL,
    [UserID] uniqueidentifier NOT NULL,
    [PaperID] uniqueidentifier NOT NULL,
    [Status] nvarchar(20) CONSTRAINT [DF__PDF_REQUE__Statu__214BF109] DEFAULT ('pending') NOT NULL,
    [UserMessage] nvarchar(1000) NULL,
    [AdminNote] nvarchar(1000) NULL,
    [RequestedAt] datetime2(7) CONSTRAINT [DF__PDF_REQUE__Reque__22401542] DEFAULT (sysdatetime()) NOT NULL,
    [ResolvedAt] datetime2(7) NULL,
    [ResolvedByAdminID] uniqueidentifier NULL
);
GO

CREATE TABLE [dbo].[PUBLICATION_TREND] (
    [TrendID] uniqueidentifier CONSTRAINT [DF__PUBLICATI__Trend__2B0A656D] DEFAULT (newid()) NOT NULL,
    [PeriodType] nvarchar(10) NOT NULL,
    [PeriodValue] nvarchar(15) NOT NULL,
    [TrendTarget] nvarchar(10) NOT NULL,
    [TargetID] uniqueidentifier NOT NULL,
    [PaperCount] int CONSTRAINT [DF__PUBLICATI__Paper__2DE6D218] DEFAULT ((0)) NOT NULL,
    [CitationCount] int CONSTRAINT [DF__PUBLICATI__Citat__2EDAF651] DEFAULT ((0)) NOT NULL,
    [GrowthRate] decimal(10,4) NULL,
    [CalculatedAt] datetime2(0) CONSTRAINT [DF__PUBLICATI__Calcu__2FCF1A8A] DEFAULT (sysdatetime()) NOT NULL
);
GO

CREATE TABLE [dbo].[REPORT] (
    [ReportID] uniqueidentifier CONSTRAINT [DF__REPORT__ReportID__4E53A1AA] DEFAULT (newid()) NOT NULL,
    [UserID] uniqueidentifier NOT NULL,
    [FieldID] uniqueidentifier NULL,
    [ReportName] nvarchar(300) NOT NULL,
    [PeriodStart] date NOT NULL,
    [PeriodEnd] date NOT NULL,
    [Status] nvarchar(20) CONSTRAINT [DF__REPORT__Status__4F47C5E3] DEFAULT ('generating') NOT NULL,
    [Format] nvarchar(5) CONSTRAINT [DF__REPORT__Format__51300E55] DEFAULT ('pdf') NOT NULL,
    [FileURL] nvarchar(1000) NULL,
    [CreatedAt] datetime2(0) CONSTRAINT [DF__REPORT__CreatedA__531856C7] DEFAULT (sysdatetime()) NOT NULL
);
GO

CREATE TABLE [dbo].[RESEARCH_FIELD] (
    [FieldID] uniqueidentifier CONSTRAINT [DF__RESEARCH___Field__70DDC3D8] DEFAULT (newid()) NOT NULL,
    [ParentFieldID] uniqueidentifier NULL,
    [FieldName] nvarchar(200) NOT NULL,
    [IsTracked] bit CONSTRAINT [DF__RESEARCH___IsTra__71D1E811] DEFAULT ((1)) NOT NULL,
    [Description] nvarchar(500) NULL
);
GO

CREATE TABLE [dbo].[RESEARCH_PAPER] (
    [PaperID] uniqueidentifier CONSTRAINT [DF__RESEARCH___Paper__07C12930] DEFAULT (newid()) NOT NULL,
    [SourceID] uniqueidentifier NOT NULL,
    [JournalID] uniqueidentifier NULL,
    [FieldID] uniqueidentifier NULL,
    [Title] nvarchar(1000) NOT NULL,
    [Abstract] nvarchar(MAX) NULL,
    [DOI] nvarchar(200) NULL,
    [PubDate] date NULL,
    [PubYear] smallint NULL,
    [CitationCount] int CONSTRAINT [DF__RESEARCH___Citat__08B54D69] DEFAULT ((0)) NOT NULL,
    [IsOpenAccess] bit CONSTRAINT [DF__RESEARCH___IsOpe__09A971A2] DEFAULT ((0)) NOT NULL,
    [CreatedAt] datetime2(0) CONSTRAINT [DF__RESEARCH___Creat__0A9D95DB] DEFAULT (sysdatetime()) NOT NULL,
    [PdfUrl] nvarchar(500) NULL,
    [Type] nvarchar(50) NULL,
    [OpenAlexWorkId] nvarchar(500) NULL
);
GO

CREATE TABLE [dbo].[RESEARCH_TOPIC] (
    [TopicID] uniqueidentifier CONSTRAINT [DF__RESEARCH___Topic__1CBC4616] DEFAULT (newid()) NOT NULL,
    [FieldID] uniqueidentifier NULL,
    [TopicName] nvarchar(300) NOT NULL,
    [IsTrending] bit CONSTRAINT [DF__RESEARCH___IsTre__1DB06A4F] DEFAULT ((0)) NOT NULL,
    [TrendScore] decimal(10,4) CONSTRAINT [DF__RESEARCH___Trend__1EA48E88] DEFAULT ((0)) NOT NULL,
    [PaperCount] int CONSTRAINT [DF__RESEARCH___Paper__1F98B2C1] DEFAULT ((0)) NOT NULL,
    [UpdatedAt] datetime2(0) CONSTRAINT [DF__RESEARCH___Updat__208CD6FA] DEFAULT (sysdatetime()) NOT NULL
);
GO

CREATE TABLE [dbo].[ROLE] (
    [RoleID] uniqueidentifier CONSTRAINT [DF__ROLE__RoleID__4AB81AF0] DEFAULT (newid()) NOT NULL,
    [RoleName] nvarchar(50) NOT NULL,
    [Description] nvarchar(500) NULL
);
GO

CREATE TABLE [dbo].[SEARCH_KEYWORD] (
    [SearchKeywordID] uniqueidentifier CONSTRAINT [DF__SEARCH_KE__Searc__02C769E9] DEFAULT (newid()) NOT NULL,
    [KeywordText] nvarchar(500) NOT NULL,
    [NormalizedText] nvarchar(500) NOT NULL,
    [SearchCount] int CONSTRAINT [DF__SEARCH_KE__Searc__03BB8E22] DEFAULT ((1)) NOT NULL,
    [LastSearchedAt] datetime2(7) CONSTRAINT [DF__SEARCH_KE__LastS__04AFB25B] DEFAULT (getdate()) NOT NULL,
    [CreatedAt] datetime2(7) CONSTRAINT [DF__SEARCH_KE__Creat__05A3D694] DEFAULT (getdate()) NOT NULL
);
GO

CREATE TABLE [dbo].[SYNC_LOG] (
    [LogID] uniqueidentifier CONSTRAINT [DF__SYNC_LOG__LogID__656C112C] DEFAULT (newid()) NOT NULL,
    [SourceID] uniqueidentifier NOT NULL,
    [SyncType] nvarchar(15) NOT NULL,
    [IsManual] bit CONSTRAINT [DF__SYNC_LOG__IsManu__6754599E] DEFAULT ((0)) NOT NULL,
    [Status] nvarchar(20) CONSTRAINT [DF__SYNC_LOG__Status__68487DD7] DEFAULT ('running') NOT NULL,
    [PapersFetched] int CONSTRAINT [DF__SYNC_LOG__Papers__6A30C649] DEFAULT ((0)) NULL,
    [PapersInserted] int CONSTRAINT [DF__SYNC_LOG__Papers__6B24EA82] DEFAULT ((0)) NULL,
    [ErrorMessage] nvarchar(MAX) NULL,
    [StartedAt] datetime2(0) CONSTRAINT [DF__SYNC_LOG__Starte__6C190EBB] DEFAULT (sysdatetime()) NOT NULL,
    [CompletedAt] datetime2(0) NULL,
    [PapersUpdated] int CONSTRAINT [DF_SYNC_LOG_PapersUpdated] DEFAULT ((0)) NULL
);
GO

CREATE TABLE [dbo].[SYSTEM_CONFIG] (
    [ConfigID] uniqueidentifier CONSTRAINT [DF__SYSTEM_CO__Confi__662B2B3B] DEFAULT (newid()) NOT NULL,
    [ConfigKey] nvarchar(100) NOT NULL,
    [ConfigValue] nvarchar(500) NOT NULL,
    [Description] nvarchar(500) NULL,
    [UpdatedAt] datetime2(0) CONSTRAINT [DF__SYSTEM_CO__Updat__671F4F74] DEFAULT (sysdatetime()) NOT NULL,
    [UpdatedBy] uniqueidentifier NULL
);
GO

CREATE TABLE [dbo].[TOPIC_KEYWORD] (
    [TopicID] uniqueidentifier NOT NULL,
    [KeywordID] uniqueidentifier NOT NULL,
    [Weight] decimal(5,4) CONSTRAINT [DF__TOPIC_KEY__Weigh__245D67DE] DEFAULT ((1.0)) NOT NULL
);
GO

CREATE TABLE [dbo].[TRENDING_TOPIC] (
    [TrendingTopicID] uniqueidentifier CONSTRAINT [DF__TRENDING___Trend__19AACF41] DEFAULT (newid()) NOT NULL,
    [TopicName] nvarchar(300) NOT NULL,
    [PaperCount] int CONSTRAINT [DF__TRENDING___Paper__1A9EF37A] DEFAULT ((0)) NOT NULL,
    [Source] nvarchar(50) CONSTRAINT [DF__TRENDING___Sourc__1B9317B3] DEFAULT ('openalex') NOT NULL,
    [DisplayOrder] int CONSTRAINT [DF__TRENDING___Displ__1C873BEC] DEFAULT ((0)) NOT NULL,
    [UpdatedAt] datetime2(7) CONSTRAINT [DF__TRENDING___Updat__1D7B6025] DEFAULT (sysutcdatetime()) NOT NULL
);
GO

CREATE TABLE [dbo].[USER] (
    [UserID] uniqueidentifier CONSTRAINT [DF__USER__UserID__4E88ABD4] DEFAULT (newid()) NOT NULL,
    [RoleID] uniqueidentifier NOT NULL,
    [Email] nvarchar(255) NOT NULL,
    [PasswordHash] nvarchar(255) NOT NULL,
    [FullName] nvarchar(200) NOT NULL,
    [Institution] nvarchar(300) NULL,
    [IsActive] bit CONSTRAINT [DF__USER__IsActive__4F7CD00D] DEFAULT ((1)) NOT NULL,
    [CreatedAt] datetime2(0) CONSTRAINT [DF__USER__CreatedAt__5070F446] DEFAULT (sysdatetime()) NOT NULL,
    [LastLoginAt] datetime2(0) NULL,
    [RoleExpiryAt] datetime2(7) NULL,
    [BackgroundUrl] nvarchar(500) NULL
);
GO

CREATE TABLE [dbo].[USER_READING_HISTORY] (
    [ReadingHistoryID] uniqueidentifier CONSTRAINT [DF__USER_READ__Readi__0D0FEE32] DEFAULT (newid()) NOT NULL,
    [UserID] uniqueidentifier NOT NULL,
    [PaperID] uniqueidentifier NOT NULL,
    [ViewedAt] datetime2(7) CONSTRAINT [DF__USER_READ__Viewe__0E04126B] DEFAULT (getdate()) NOT NULL
);
GO

CREATE TABLE [dbo].[USER_SEARCH_HISTORY] (
    [SearchHistoryID] uniqueidentifier CONSTRAINT [DF__USER_SEAR__Searc__14E61A24] DEFAULT (newid()) NOT NULL,
    [UserID] uniqueidentifier NOT NULL,
    [SearchText] nvarchar(500) NOT NULL,
    [SearchType] nvarchar(20) NOT NULL,
    [SearchedAt] datetime2(7) CONSTRAINT [DF__USER_SEAR__Searc__15DA3E5D] DEFAULT (sysutcdatetime()) NOT NULL
);
GO

CREATE TABLE [dbo].[USER_SESSION] (
    [SessionID] uniqueidentifier CONSTRAINT [DF__USER_SESS__Sessi__5441852A] DEFAULT (newid()) NOT NULL,
    [UserID] uniqueidentifier NOT NULL,
    [TokenHash] nvarchar(500) NOT NULL,
    [ExpiresAt] datetime2(0) NOT NULL,
    [CreatedAt] datetime2(0) CONSTRAINT [DF__USER_SESS__Creat__5535A963] DEFAULT (sysdatetime()) NOT NULL,
    [RefreshTokenHash] nvarchar(500) NULL,
    [RefreshExpiresAt] datetime2(7) NULL
);
GO

CREATE TABLE [dbo].[USER_USAGE] (
    [UsageID] uniqueidentifier CONSTRAINT [DF__USER_USAG__Usage__6BE40491] DEFAULT (newid()) NOT NULL,
    [UserID] uniqueidentifier NOT NULL,
    [UsageMonth] char(7) NOT NULL,
    [SearchCount] int CONSTRAINT [DF__USER_USAG__Searc__6CD828CA] DEFAULT ((0)) NOT NULL,
    [ViewCount] int CONSTRAINT [DF__USER_USAG__ViewC__6DCC4D03] DEFAULT ((0)) NOT NULL,
    [ChartViewCount] int CONSTRAINT [DF__USER_USAG__Chart__6EC0713C] DEFAULT ((0)) NOT NULL,
    [LastUpdated] datetime2(0) CONSTRAINT [DF__USER_USAG__LastU__6FB49575] DEFAULT (sysdatetime()) NOT NULL
);
GO

CREATE TABLE [dbo].[VERIFICATION_TOKEN] (
    [TokenID] uniqueidentifier CONSTRAINT [DF__VERIFICAT__Token__59FA5E80] DEFAULT (newid()) NOT NULL,
    [UserID] uniqueidentifier NOT NULL,
    [Token] nvarchar(255) NOT NULL,
    [TokenType] nvarchar(20) NOT NULL,
    [ExpiresAt] datetime2(0) NOT NULL,
    [CreatedAt] datetime2(0) CONSTRAINT [DF__VERIFICAT__Creat__5BE2A6F2] DEFAULT (sysdatetime()) NOT NULL,
    [IsUsed] bit CONSTRAINT [DF__VERIFICAT__IsUse__5CD6CB2B] DEFAULT ((0)) NOT NULL
);
GO

ALTER TABLE [dbo].[API_SOURCE] ADD CONSTRAINT [PK_API_SOURCE] PRIMARY KEY CLUSTERED ([SourceID] ASC);
GO

ALTER TABLE [dbo].[API_SOURCE] ADD CONSTRAINT [UK_API_SOURCE_Name] UNIQUE NONCLUSTERED ([SourceName] ASC);
GO

ALTER TABLE [dbo].[AUDIT_LOG] ADD CONSTRAINT [PK_AUDIT_LOG] PRIMARY KEY CLUSTERED ([AuditID] ASC);
GO

ALTER TABLE [dbo].[AUTO_SYNC_KEYWORD] ADD CONSTRAINT [PK__AUTO_SYN__37C135C113DE7D47] PRIMARY KEY CLUSTERED ([KeywordID] ASC);
GO

ALTER TABLE [dbo].[AUTHOR] ADD CONSTRAINT [PK_AUTHOR] PRIMARY KEY CLUSTERED ([AuthorID] ASC);
GO

ALTER TABLE [dbo].[BOOKMARK] ADD CONSTRAINT [PK_BOOKMARK] PRIMARY KEY CLUSTERED ([BookmarkID] ASC);
GO

ALTER TABLE [dbo].[BOOKMARK_COLLECTION] ADD CONSTRAINT [PK_BOOKMARK_COLLECTION] PRIMARY KEY CLUSTERED ([CollectionID] ASC);
GO

ALTER TABLE [dbo].[BOOKMARK_COLLECTION] ADD CONSTRAINT [UQ_BC_User_Name] UNIQUE NONCLUSTERED ([UserID] ASC, [Name] ASC);
GO

ALTER TABLE [dbo].[DASHBOARD_WIDGET] ADD CONSTRAINT [PK_DASHBOARD_WIDGET] PRIMARY KEY CLUSTERED ([WidgetID] ASC);
GO

ALTER TABLE [dbo].[FOLLOW] ADD CONSTRAINT [PK_FOLLOW] PRIMARY KEY CLUSTERED ([FollowID] ASC);
GO

ALTER TABLE [dbo].[JOURNAL] ADD CONSTRAINT [PK_JOURNAL] PRIMARY KEY CLUSTERED ([JournalID] ASC);
GO

ALTER TABLE [dbo].[KEYWORD] ADD CONSTRAINT [PK_KEYWORD] PRIMARY KEY CLUSTERED ([KeywordID] ASC);
GO

ALTER TABLE [dbo].[KEYWORD] ADD CONSTRAINT [UK_KEYWORD_Normalized] UNIQUE NONCLUSTERED ([NormalizedText] ASC);
GO

ALTER TABLE [dbo].[NOTIFICATION] ADD CONSTRAINT [PK_NOTIFICATION] PRIMARY KEY CLUSTERED ([NotifID] ASC);
GO

ALTER TABLE [dbo].[PAPER_AUTHOR] ADD CONSTRAINT [PK_PAPER_AUTHOR] PRIMARY KEY CLUSTERED ([PaperID] ASC, [AuthorID] ASC);
GO

ALTER TABLE [dbo].[PAPER_CACHE] ADD CONSTRAINT [PK_PAPER_CACHE] PRIMARY KEY CLUSTERED ([PaperID] ASC);
GO

ALTER TABLE [dbo].[PAPER_KEYWORD] ADD CONSTRAINT [PK_PAPER_KEYWORD] PRIMARY KEY CLUSTERED ([PaperID] ASC, [KeywordID] ASC);
GO

ALTER TABLE [dbo].[PAPER_RATING] ADD CONSTRAINT [PK_PAPER_RATING] PRIMARY KEY CLUSTERED ([RatingID] ASC);
GO

ALTER TABLE [dbo].[PAPER_RATING] ADD CONSTRAINT [UK_PAPER_RATING_UserPaper] UNIQUE NONCLUSTERED ([UserID] ASC, [PaperID] ASC);
GO

ALTER TABLE [dbo].[PDF_REQUEST] ADD CONSTRAINT [PK_PDF_REQUEST] PRIMARY KEY CLUSTERED ([RequestID] ASC);
GO

ALTER TABLE [dbo].[PUBLICATION_TREND] ADD CONSTRAINT [PK_PUBLICATION_TREND] PRIMARY KEY CLUSTERED ([TrendID] ASC);
GO

ALTER TABLE [dbo].[PUBLICATION_TREND] ADD CONSTRAINT [UK_TREND_Period] UNIQUE NONCLUSTERED ([TrendTarget] ASC, [TargetID] ASC, [PeriodType] ASC, [PeriodValue] ASC);
GO

ALTER TABLE [dbo].[REPORT] ADD CONSTRAINT [PK_REPORT] PRIMARY KEY CLUSTERED ([ReportID] ASC);
GO

ALTER TABLE [dbo].[RESEARCH_FIELD] ADD CONSTRAINT [PK_RESEARCH_FIELD] PRIMARY KEY CLUSTERED ([FieldID] ASC);
GO

ALTER TABLE [dbo].[RESEARCH_FIELD] ADD CONSTRAINT [UK_FIELD_Name] UNIQUE NONCLUSTERED ([FieldName] ASC);
GO

ALTER TABLE [dbo].[RESEARCH_PAPER] ADD CONSTRAINT [PK_RESEARCH_PAPER] PRIMARY KEY CLUSTERED ([PaperID] ASC);
GO

ALTER TABLE [dbo].[RESEARCH_TOPIC] ADD CONSTRAINT [PK_RESEARCH_TOPIC] PRIMARY KEY CLUSTERED ([TopicID] ASC);
GO

ALTER TABLE [dbo].[ROLE] ADD CONSTRAINT [PK_ROLE] PRIMARY KEY CLUSTERED ([RoleID] ASC);
GO

ALTER TABLE [dbo].[ROLE] ADD CONSTRAINT [UK_ROLE_Name] UNIQUE NONCLUSTERED ([RoleName] ASC);
GO

ALTER TABLE [dbo].[SEARCH_KEYWORD] ADD CONSTRAINT [PK__SEARCH_K__3F572619D718E6E1] PRIMARY KEY CLUSTERED ([SearchKeywordID] ASC);
GO

ALTER TABLE [dbo].[SEARCH_KEYWORD] ADD CONSTRAINT [UQ__SEARCH_K__FC15A72ABAFA19D6] UNIQUE NONCLUSTERED ([NormalizedText] ASC);
GO

ALTER TABLE [dbo].[SYNC_LOG] ADD CONSTRAINT [PK_SYNC_LOG] PRIMARY KEY CLUSTERED ([LogID] ASC);
GO

ALTER TABLE [dbo].[SYSTEM_CONFIG] ADD CONSTRAINT [PK_SYSTEM_CONFIG] PRIMARY KEY CLUSTERED ([ConfigID] ASC);
GO

ALTER TABLE [dbo].[SYSTEM_CONFIG] ADD CONSTRAINT [UK_CONFIG_Key] UNIQUE NONCLUSTERED ([ConfigKey] ASC);
GO

ALTER TABLE [dbo].[TOPIC_KEYWORD] ADD CONSTRAINT [PK_TOPIC_KEYWORD] PRIMARY KEY CLUSTERED ([TopicID] ASC, [KeywordID] ASC);
GO

ALTER TABLE [dbo].[TRENDING_TOPIC] ADD CONSTRAINT [PK_TRENDING_TOPIC] PRIMARY KEY CLUSTERED ([TrendingTopicID] ASC);
GO

ALTER TABLE [dbo].[USER] ADD CONSTRAINT [PK_USER] PRIMARY KEY CLUSTERED ([UserID] ASC);
GO

ALTER TABLE [dbo].[USER] ADD CONSTRAINT [UK_USER_Email] UNIQUE NONCLUSTERED ([Email] ASC);
GO

ALTER TABLE [dbo].[USER_READING_HISTORY] ADD CONSTRAINT [PK_USER_READING_HISTORY] PRIMARY KEY CLUSTERED ([ReadingHistoryID] ASC);
GO

ALTER TABLE [dbo].[USER_SEARCH_HISTORY] ADD CONSTRAINT [PK_USER_SEARCH_HISTORY] PRIMARY KEY CLUSTERED ([SearchHistoryID] ASC);
GO

ALTER TABLE [dbo].[USER_SESSION] ADD CONSTRAINT [PK_USER_SESSION] PRIMARY KEY CLUSTERED ([SessionID] ASC);
GO

ALTER TABLE [dbo].[USER_USAGE] ADD CONSTRAINT [PK_USER_USAGE] PRIMARY KEY CLUSTERED ([UsageID] ASC);
GO

ALTER TABLE [dbo].[USER_USAGE] ADD CONSTRAINT [UK_USAGE_UserMonth] UNIQUE NONCLUSTERED ([UserID] ASC, [UsageMonth] ASC);
GO

ALTER TABLE [dbo].[VERIFICATION_TOKEN] ADD CONSTRAINT [PK_VERIFICATION_TOKEN] PRIMARY KEY CLUSTERED ([TokenID] ASC);
GO

ALTER TABLE [dbo].[VERIFICATION_TOKEN] ADD CONSTRAINT [UK_VT_Token] UNIQUE NONCLUSTERED ([Token] ASC);
GO

ALTER TABLE [dbo].[BOOKMARK] ADD CONSTRAINT [CK_BOOKMARK_OneTarget] CHECK ([PaperID] IS NOT NULL AND [KeywordID] IS NULL OR [PaperID] IS NULL AND [KeywordID] IS NOT NULL);
GO

ALTER TABLE [dbo].[DASHBOARD_WIDGET] ADD CONSTRAINT [CK__DASHBOARD__Widge__29E1370A] CHECK ([WidgetType]='line_chart' OR [WidgetType]='bar_chart' OR [WidgetType]='pie_chart' OR [WidgetType]='stat_card');
GO

ALTER TABLE [dbo].[DASHBOARD_WIDGET] ADD CONSTRAINT [CK__DASHBOARD__Widge__59C55456] CHECK ([WidgetType]='stat_card' OR [WidgetType]='pie_chart' OR [WidgetType]='bar_chart' OR [WidgetType]='line_chart');
GO

ALTER TABLE [dbo].[DASHBOARD_WIDGET] ADD CONSTRAINT [CK_DW_Position] CHECK ([PositionX]>=(0) AND [PositionY]>=(0));
GO

ALTER TABLE [dbo].[DASHBOARD_WIDGET] ADD CONSTRAINT [CK_DW_Size] CHECK ([Width]>=(1) AND [Height]>=(1));
GO

ALTER TABLE [dbo].[FOLLOW] ADD CONSTRAINT [CK_FOLLOW_OneTarget] CHECK ((((case when [JournalID] IS NOT NULL then (1) else (0) end+case when [TopicID] IS NOT NULL then (1) else (0) end)+case when [KeywordID] IS NOT NULL then (1) else (0) end)+case when [AuthorID] IS NOT NULL then (1) else (0) end)=(1));
GO

ALTER TABLE [dbo].[JOURNAL] ADD CONSTRAINT [CK__JOURNAL__Quartil__76969D2E] CHECK ([Quartile]='Q4' OR [Quartile]='Q3' OR [Quartile]='Q2' OR [Quartile]='Q1');
GO

ALTER TABLE [dbo].[NOTIFICATION] ADD CONSTRAINT [CK__NOTIFICATI__Type__44CA3770] CHECK ([Type]='upgrade_prompt' OR [Type]='system' OR [Type]='trend_alert' OR [Type]='new_paper');
GO

ALTER TABLE [dbo].[PAPER_AUTHOR] ADD CONSTRAINT [CK_PA_AuthorOrder] CHECK ([AuthorOrder]>=(1));
GO

ALTER TABLE [dbo].[PAPER_KEYWORD] ADD CONSTRAINT [CK_PK_RelevanceScore] CHECK ([RelevanceScore] IS NULL OR [RelevanceScore]>=(0) AND [RelevanceScore]<=(1));
GO

ALTER TABLE [dbo].[PAPER_RATING] ADD CONSTRAINT [CK_PAPER_RATING_Score] CHECK ([Score]>=(1) AND [Score]<=(5));
GO

ALTER TABLE [dbo].[PDF_REQUEST] ADD CONSTRAINT [CK_PDF_REQUEST_Status] CHECK ([Status]='rejected' OR [Status]='fulfilled' OR [Status]='pending');
GO

ALTER TABLE [dbo].[PUBLICATION_TREND] ADD CONSTRAINT [CK__PUBLICATI__Perio__2BFE89A6] CHECK ([PeriodType]='yearly' OR [PeriodType]='quarterly' OR [PeriodType]='monthly');
GO

ALTER TABLE [dbo].[PUBLICATION_TREND] ADD CONSTRAINT [CK__PUBLICATI__Trend__2CF2ADDF] CHECK ([TrendTarget]='field' OR [TrendTarget]='journal' OR [TrendTarget]='topic' OR [TrendTarget]='keyword');
GO

ALTER TABLE [dbo].[REPORT] ADD CONSTRAINT [CK__REPORT__Format__2AD55B43] CHECK ([Format]='pdf' OR [Format]='csv');
GO

ALTER TABLE [dbo].[REPORT] ADD CONSTRAINT [CK__REPORT__Format__5224328E] CHECK ([Format]='csv' OR [Format]='pdf');
GO

ALTER TABLE [dbo].[REPORT] ADD CONSTRAINT [CK__REPORT__Status__2BC97F7C] CHECK ([Status]='generating' OR [Status]='completed' OR [Status]='failed');
GO

ALTER TABLE [dbo].[REPORT] ADD CONSTRAINT [CK__REPORT__Status__503BEA1C] CHECK ([Status]='failed' OR [Status]='completed' OR [Status]='generating');
GO

ALTER TABLE [dbo].[REPORT] ADD CONSTRAINT [CK_REPORT_Period] CHECK ([PeriodEnd]>=[PeriodStart]);
GO

ALTER TABLE [dbo].[RESEARCH_PAPER] ADD CONSTRAINT [CK_PAPER_PubYear] CHECK ([PubYear]>=(1900) AND [PubYear]<=(2100));
GO

ALTER TABLE [dbo].[SYNC_LOG] ADD CONSTRAINT [CK__SYNC_LOG__Status__693CA210] CHECK ([Status]='failed' OR [Status]='completed' OR [Status]='running');
GO

ALTER TABLE [dbo].[SYNC_LOG] ADD CONSTRAINT [CK__SYNC_LOG__SyncTy__66603565] CHECK ([SyncType]='incremental' OR [SyncType]='full');
GO

ALTER TABLE [dbo].[TOPIC_KEYWORD] ADD CONSTRAINT [CK_TK_Weight] CHECK ([Weight]>=(0) AND [Weight]<=(1));
GO

ALTER TABLE [dbo].[USER_USAGE] ADD CONSTRAINT [CK_USAGE_Month] CHECK ([UsageMonth] like '[0-9][0-9][0-9][0-9]-[0-1][0-9]');
GO

ALTER TABLE [dbo].[VERIFICATION_TOKEN] ADD CONSTRAINT [CK__VERIFICAT__Token__5AEE82B9] CHECK ([TokenType]='PASSWORD_RESET' OR [TokenType]='EMAIL_VERIFICATION');
GO

ALTER TABLE [dbo].[AUDIT_LOG] ADD CONSTRAINT [FK_AUDIT_Admin] FOREIGN KEY ([AdminID]) REFERENCES [dbo].[USER] ([UserID]);
GO

ALTER TABLE [dbo].[AUTHOR] ADD CONSTRAINT [FK_AUTHOR_Source] FOREIGN KEY ([SourceID]) REFERENCES [dbo].[API_SOURCE] ([SourceID]);
GO

ALTER TABLE [dbo].[BOOKMARK] ADD CONSTRAINT [FK_BM_Keyword] FOREIGN KEY ([KeywordID]) REFERENCES [dbo].[KEYWORD] ([KeywordID]);
GO

ALTER TABLE [dbo].[BOOKMARK] ADD CONSTRAINT [FK_BM_Paper] FOREIGN KEY ([PaperID]) REFERENCES [dbo].[RESEARCH_PAPER] ([PaperID]);
GO

ALTER TABLE [dbo].[BOOKMARK] ADD CONSTRAINT [FK_BM_User] FOREIGN KEY ([UserID]) REFERENCES [dbo].[USER] ([UserID]) ON DELETE CASCADE;
GO

ALTER TABLE [dbo].[BOOKMARK_COLLECTION] ADD CONSTRAINT [FK_BC_User] FOREIGN KEY ([UserID]) REFERENCES [dbo].[USER] ([UserID]) ON DELETE CASCADE;
GO

ALTER TABLE [dbo].[DASHBOARD_WIDGET] ADD CONSTRAINT [FK_DW_User] FOREIGN KEY ([UserID]) REFERENCES [dbo].[USER] ([UserID]) ON DELETE CASCADE;
GO

ALTER TABLE [dbo].[FOLLOW] ADD CONSTRAINT [FK_FOLLOW_Author] FOREIGN KEY ([AuthorID]) REFERENCES [dbo].[AUTHOR] ([AuthorID]);
GO

ALTER TABLE [dbo].[FOLLOW] ADD CONSTRAINT [FK_FOLLOW_Journal] FOREIGN KEY ([JournalID]) REFERENCES [dbo].[JOURNAL] ([JournalID]);
GO

ALTER TABLE [dbo].[FOLLOW] ADD CONSTRAINT [FK_FOLLOW_Keyword] FOREIGN KEY ([KeywordID]) REFERENCES [dbo].[KEYWORD] ([KeywordID]);
GO

ALTER TABLE [dbo].[FOLLOW] ADD CONSTRAINT [FK_FOLLOW_Topic] FOREIGN KEY ([TopicID]) REFERENCES [dbo].[RESEARCH_TOPIC] ([TopicID]);
GO

ALTER TABLE [dbo].[FOLLOW] ADD CONSTRAINT [FK_FOLLOW_User] FOREIGN KEY ([UserID]) REFERENCES [dbo].[USER] ([UserID]) ON DELETE CASCADE;
GO

ALTER TABLE [dbo].[JOURNAL] ADD CONSTRAINT [FK_JOURNAL_Field] FOREIGN KEY ([FieldID]) REFERENCES [dbo].[RESEARCH_FIELD] ([FieldID]);
GO

ALTER TABLE [dbo].[JOURNAL] ADD CONSTRAINT [FK_JOURNAL_Source] FOREIGN KEY ([SourceID]) REFERENCES [dbo].[API_SOURCE] ([SourceID]);
GO

ALTER TABLE [dbo].[KEYWORD] ADD CONSTRAINT [FK_KEYWORD_Field] FOREIGN KEY ([FieldID]) REFERENCES [dbo].[RESEARCH_FIELD] ([FieldID]);
GO

ALTER TABLE [dbo].[NOTIFICATION] ADD CONSTRAINT [FK_NOTIF_Journal] FOREIGN KEY ([RelatedJournalID]) REFERENCES [dbo].[JOURNAL] ([JournalID]);
GO

ALTER TABLE [dbo].[NOTIFICATION] ADD CONSTRAINT [FK_NOTIF_Keyword] FOREIGN KEY ([RelatedKeywordID]) REFERENCES [dbo].[KEYWORD] ([KeywordID]);
GO

ALTER TABLE [dbo].[NOTIFICATION] ADD CONSTRAINT [FK_NOTIF_Paper] FOREIGN KEY ([RelatedPaperID]) REFERENCES [dbo].[RESEARCH_PAPER] ([PaperID]);
GO

ALTER TABLE [dbo].[NOTIFICATION] ADD CONSTRAINT [FK_NOTIF_Topic] FOREIGN KEY ([RelatedTopicID]) REFERENCES [dbo].[RESEARCH_TOPIC] ([TopicID]);
GO

ALTER TABLE [dbo].[NOTIFICATION] ADD CONSTRAINT [FK_NOTIF_User] FOREIGN KEY ([UserID]) REFERENCES [dbo].[USER] ([UserID]) ON DELETE CASCADE;
GO

ALTER TABLE [dbo].[PAPER_AUTHOR] ADD CONSTRAINT [FK_PA_Author] FOREIGN KEY ([AuthorID]) REFERENCES [dbo].[AUTHOR] ([AuthorID]);
GO

ALTER TABLE [dbo].[PAPER_AUTHOR] ADD CONSTRAINT [FK_PA_Paper] FOREIGN KEY ([PaperID]) REFERENCES [dbo].[RESEARCH_PAPER] ([PaperID]) ON DELETE CASCADE;
GO

ALTER TABLE [dbo].[PAPER_KEYWORD] ADD CONSTRAINT [FK_PK_Keyword] FOREIGN KEY ([KeywordID]) REFERENCES [dbo].[KEYWORD] ([KeywordID]);
GO

ALTER TABLE [dbo].[PAPER_KEYWORD] ADD CONSTRAINT [FK_PK_Paper] FOREIGN KEY ([PaperID]) REFERENCES [dbo].[RESEARCH_PAPER] ([PaperID]) ON DELETE CASCADE;
GO

ALTER TABLE [dbo].[PAPER_RATING] ADD CONSTRAINT [FK_PAPER_RATING_Paper] FOREIGN KEY ([PaperID]) REFERENCES [dbo].[RESEARCH_PAPER] ([PaperID]);
GO

ALTER TABLE [dbo].[PAPER_RATING] ADD CONSTRAINT [FK_PAPER_RATING_User] FOREIGN KEY ([UserID]) REFERENCES [dbo].[USER] ([UserID]);
GO

ALTER TABLE [dbo].[PDF_REQUEST] ADD CONSTRAINT [FK_PDF_REQUEST_Admin] FOREIGN KEY ([ResolvedByAdminID]) REFERENCES [dbo].[USER] ([UserID]);
GO

ALTER TABLE [dbo].[PDF_REQUEST] ADD CONSTRAINT [FK_PDF_REQUEST_Paper] FOREIGN KEY ([PaperID]) REFERENCES [dbo].[RESEARCH_PAPER] ([PaperID]);
GO

ALTER TABLE [dbo].[PDF_REQUEST] ADD CONSTRAINT [FK_PDF_REQUEST_User] FOREIGN KEY ([UserID]) REFERENCES [dbo].[USER] ([UserID]);
GO

ALTER TABLE [dbo].[REPORT] ADD CONSTRAINT [FK_REPORT_Field] FOREIGN KEY ([FieldID]) REFERENCES [dbo].[RESEARCH_FIELD] ([FieldID]);
GO

ALTER TABLE [dbo].[REPORT] ADD CONSTRAINT [FK_REPORT_User] FOREIGN KEY ([UserID]) REFERENCES [dbo].[USER] ([UserID]) ON DELETE CASCADE;
GO

ALTER TABLE [dbo].[RESEARCH_FIELD] ADD CONSTRAINT [FK_FIELD_Parent] FOREIGN KEY ([ParentFieldID]) REFERENCES [dbo].[RESEARCH_FIELD] ([FieldID]);
GO

ALTER TABLE [dbo].[RESEARCH_PAPER] ADD CONSTRAINT [FK_PAPER_Field] FOREIGN KEY ([FieldID]) REFERENCES [dbo].[RESEARCH_FIELD] ([FieldID]);
GO

ALTER TABLE [dbo].[RESEARCH_PAPER] ADD CONSTRAINT [FK_PAPER_Journal] FOREIGN KEY ([JournalID]) REFERENCES [dbo].[JOURNAL] ([JournalID]);
GO

ALTER TABLE [dbo].[RESEARCH_PAPER] ADD CONSTRAINT [FK_PAPER_Source] FOREIGN KEY ([SourceID]) REFERENCES [dbo].[API_SOURCE] ([SourceID]);
GO

ALTER TABLE [dbo].[RESEARCH_TOPIC] ADD CONSTRAINT [FK_TOPIC_Field] FOREIGN KEY ([FieldID]) REFERENCES [dbo].[RESEARCH_FIELD] ([FieldID]);
GO

ALTER TABLE [dbo].[SYNC_LOG] ADD CONSTRAINT [FK_SYNC_Source] FOREIGN KEY ([SourceID]) REFERENCES [dbo].[API_SOURCE] ([SourceID]);
GO

ALTER TABLE [dbo].[SYSTEM_CONFIG] ADD CONSTRAINT [FK_CONFIG_UpdatedBy] FOREIGN KEY ([UpdatedBy]) REFERENCES [dbo].[USER] ([UserID]);
GO

ALTER TABLE [dbo].[TOPIC_KEYWORD] ADD CONSTRAINT [FK_TK_Keyword] FOREIGN KEY ([KeywordID]) REFERENCES [dbo].[KEYWORD] ([KeywordID]);
GO

ALTER TABLE [dbo].[TOPIC_KEYWORD] ADD CONSTRAINT [FK_TK_Topic] FOREIGN KEY ([TopicID]) REFERENCES [dbo].[RESEARCH_TOPIC] ([TopicID]) ON DELETE CASCADE;
GO

ALTER TABLE [dbo].[USER] ADD CONSTRAINT [FK_USER_Role] FOREIGN KEY ([RoleID]) REFERENCES [dbo].[ROLE] ([RoleID]);
GO

ALTER TABLE [dbo].[USER_READING_HISTORY] ADD CONSTRAINT [FK_READING_HISTORY_PAPER] FOREIGN KEY ([PaperID]) REFERENCES [dbo].[RESEARCH_PAPER] ([PaperID]);
GO

ALTER TABLE [dbo].[USER_READING_HISTORY] ADD CONSTRAINT [FK_READING_HISTORY_USER] FOREIGN KEY ([UserID]) REFERENCES [dbo].[USER] ([UserID]);
GO

ALTER TABLE [dbo].[USER_SEARCH_HISTORY] ADD CONSTRAINT [FK_USH_USER] FOREIGN KEY ([UserID]) REFERENCES [dbo].[USER] ([UserID]) ON DELETE CASCADE;
GO

ALTER TABLE [dbo].[USER_SESSION] ADD CONSTRAINT [FK_SESSION_User] FOREIGN KEY ([UserID]) REFERENCES [dbo].[USER] ([UserID]) ON DELETE CASCADE;
GO

ALTER TABLE [dbo].[USER_USAGE] ADD CONSTRAINT [FK_USAGE_User] FOREIGN KEY ([UserID]) REFERENCES [dbo].[USER] ([UserID]) ON DELETE CASCADE;
GO

ALTER TABLE [dbo].[VERIFICATION_TOKEN] ADD CONSTRAINT [FK_VT_User] FOREIGN KEY ([UserID]) REFERENCES [dbo].[USER] ([UserID]) ON DELETE CASCADE;
GO

CREATE NONCLUSTERED INDEX [IX_AUDIT_AdminID] ON [dbo].[AUDIT_LOG] ([AdminID] ASC);
GO

CREATE NONCLUSTERED INDEX [IX_AUDIT_CreatedAt] ON [dbo].[AUDIT_LOG] ([CreatedAt] DESC);
GO

CREATE NONCLUSTERED INDEX [IX_AUTHOR_Country] ON [dbo].[AUTHOR] ([Country] ASC);
GO

CREATE NONCLUSTERED INDEX [IX_AUTHOR_FullName] ON [dbo].[AUTHOR] ([FullName] ASC);
GO

CREATE UNIQUE NONCLUSTERED INDEX [UK_AUTHOR_External] ON [dbo].[AUTHOR] ([SourceID] ASC, [ExternalAuthorID] ASC) WHERE ([ExternalAuthorID] IS NOT NULL);
GO

CREATE NONCLUSTERED INDEX [IX_BOOKMARK_CollectionID] ON [dbo].[BOOKMARK] ([CollectionID] ASC);
GO

CREATE NONCLUSTERED INDEX [IX_BOOKMARK_UserID] ON [dbo].[BOOKMARK] ([UserID] ASC);
GO

CREATE UNIQUE NONCLUSTERED INDEX [UK_BOOKMARK_Keyword] ON [dbo].[BOOKMARK] ([UserID] ASC, [KeywordID] ASC) WHERE ([KeywordID] IS NOT NULL);
GO

CREATE UNIQUE NONCLUSTERED INDEX [UK_BOOKMARK_Paper] ON [dbo].[BOOKMARK] ([UserID] ASC, [PaperID] ASC) WHERE ([PaperID] IS NOT NULL);
GO

CREATE NONCLUSTERED INDEX [IX_FOLLOW_UserID] ON [dbo].[FOLLOW] ([UserID] ASC);
GO

CREATE UNIQUE NONCLUSTERED INDEX [UK_FOLLOW_Journal] ON [dbo].[FOLLOW] ([UserID] ASC, [JournalID] ASC) WHERE ([JournalID] IS NOT NULL);
GO

CREATE UNIQUE NONCLUSTERED INDEX [UK_FOLLOW_Keyword] ON [dbo].[FOLLOW] ([UserID] ASC, [KeywordID] ASC) WHERE ([KeywordID] IS NOT NULL);
GO

CREATE UNIQUE NONCLUSTERED INDEX [UK_FOLLOW_Topic] ON [dbo].[FOLLOW] ([UserID] ASC, [TopicID] ASC) WHERE ([TopicID] IS NOT NULL);
GO

CREATE NONCLUSTERED INDEX [IX_JOURNAL_FieldID] ON [dbo].[JOURNAL] ([FieldID] ASC);
GO

CREATE NONCLUSTERED INDEX [IX_JOURNAL_SourceID] ON [dbo].[JOURNAL] ([SourceID] ASC);
GO

CREATE UNIQUE NONCLUSTERED INDEX [UK_JOURNAL_ISSN] ON [dbo].[JOURNAL] ([ISSN] ASC) WHERE ([ISSN] IS NOT NULL);
GO

CREATE NONCLUSTERED INDEX [IX_KEYWORD_FieldID] ON [dbo].[KEYWORD] ([FieldID] ASC);
GO

CREATE NONCLUSTERED INDEX [IX_KEYWORD_Normalized] ON [dbo].[KEYWORD] ([NormalizedText] ASC);
GO

CREATE NONCLUSTERED INDEX [IX_NOTIF_CreatedAt] ON [dbo].[NOTIFICATION] ([CreatedAt] DESC);
GO

CREATE NONCLUSTERED INDEX [IX_NOTIF_UserUnread] ON [dbo].[NOTIFICATION] ([UserID] ASC, [IsRead] ASC, [CreatedAt] DESC);
GO

CREATE NONCLUSTERED INDEX [IX_PAPER_AUTHOR_PaperID_AuthorID] ON [dbo].[PAPER_AUTHOR] ([PaperID] ASC, [AuthorID] ASC);
GO

CREATE NONCLUSTERED INDEX [IX_PDF_REQUEST_StatusRequestedAt] ON [dbo].[PDF_REQUEST] ([Status] ASC, [RequestedAt] DESC);
GO

CREATE UNIQUE NONCLUSTERED INDEX [UX_PDF_REQUEST_UserPaperPending] ON [dbo].[PDF_REQUEST] ([UserID] ASC, [PaperID] ASC) WHERE ([Status]='pending');
GO

CREATE NONCLUSTERED INDEX [IX_TREND_Full] ON [dbo].[PUBLICATION_TREND] ([TrendTarget] ASC, [TargetID] ASC, [PeriodType] ASC, [PeriodValue] ASC);
GO

CREATE NONCLUSTERED INDEX [IX_TREND_Period] ON [dbo].[PUBLICATION_TREND] ([PeriodType] ASC, [PeriodValue] ASC);
GO

CREATE NONCLUSTERED INDEX [IX_TREND_Target] ON [dbo].[PUBLICATION_TREND] ([TrendTarget] ASC, [TargetID] ASC);
GO

CREATE NONCLUSTERED INDEX [IX_REPORT_Status] ON [dbo].[REPORT] ([Status] ASC);
GO

CREATE NONCLUSTERED INDEX [IX_REPORT_UserID] ON [dbo].[REPORT] ([UserID] ASC);
GO

CREATE NONCLUSTERED INDEX [IX_FIELD_ParentID] ON [dbo].[RESEARCH_FIELD] ([ParentFieldID] ASC);
GO

CREATE NONCLUSTERED INDEX [IDX_PAPER_CITATIONS] ON [dbo].[RESEARCH_PAPER] ([CitationCount] ASC);
GO

CREATE NONCLUSTERED INDEX [IDX_PAPER_CREATEDAT] ON [dbo].[RESEARCH_PAPER] ([CreatedAt] ASC);
GO

CREATE NONCLUSTERED INDEX [IDX_PAPER_PUBYEAR] ON [dbo].[RESEARCH_PAPER] ([PubYear] ASC);
GO

CREATE NONCLUSTERED INDEX [IDX_PAPER_TITLE] ON [dbo].[RESEARCH_PAPER] ([Title] ASC);
GO

CREATE NONCLUSTERED INDEX [IX_PAPER_CreatedAt] ON [dbo].[RESEARCH_PAPER] ([CreatedAt] DESC);
GO

CREATE NONCLUSTERED INDEX [IX_PAPER_FieldID] ON [dbo].[RESEARCH_PAPER] ([FieldID] ASC);
GO

CREATE NONCLUSTERED INDEX [IX_PAPER_JournalID] ON [dbo].[RESEARCH_PAPER] ([JournalID] ASC);
GO

CREATE NONCLUSTERED INDEX [IX_PAPER_PubYear] ON [dbo].[RESEARCH_PAPER] ([PubYear] DESC);
GO

CREATE UNIQUE NONCLUSTERED INDEX [UK_PAPER_DOI] ON [dbo].[RESEARCH_PAPER] ([DOI] ASC) WHERE ([DOI] IS NOT NULL);
GO

CREATE NONCLUSTERED INDEX [IX_TOPIC_FieldID] ON [dbo].[RESEARCH_TOPIC] ([FieldID] ASC);
GO

CREATE NONCLUSTERED INDEX [IX_TOPIC_IsTrending] ON [dbo].[RESEARCH_TOPIC] ([IsTrending] ASC);
GO

CREATE NONCLUSTERED INDEX [IX_TOPIC_TrendScore] ON [dbo].[RESEARCH_TOPIC] ([TrendScore] DESC);
GO

CREATE NONCLUSTERED INDEX [IX_SYNC_SourceID] ON [dbo].[SYNC_LOG] ([SourceID] ASC);
GO

CREATE NONCLUSTERED INDEX [IX_SYNC_StartedAt] ON [dbo].[SYNC_LOG] ([StartedAt] DESC);
GO

CREATE NONCLUSTERED INDEX [IX_TRENDING_TOPIC_DisplayOrder] ON [dbo].[TRENDING_TOPIC] ([DisplayOrder] ASC);
GO

CREATE NONCLUSTERED INDEX [IX_USER_IsActive] ON [dbo].[USER] ([IsActive] ASC);
GO

CREATE NONCLUSTERED INDEX [IX_USER_RoleID] ON [dbo].[USER] ([RoleID] ASC);
GO

CREATE NONCLUSTERED INDEX [IX_USH_UserID_SearchedAt] ON [dbo].[USER_SEARCH_HISTORY] ([UserID] ASC, [SearchedAt] DESC);
GO

CREATE NONCLUSTERED INDEX [IX_SESSION_ExpiresAt] ON [dbo].[USER_SESSION] ([ExpiresAt] ASC);
GO

CREATE NONCLUSTERED INDEX [IX_SESSION_RefreshTokenHash] ON [dbo].[USER_SESSION] ([RefreshTokenHash] ASC);
GO

CREATE NONCLUSTERED INDEX [IX_SESSION_UserID] ON [dbo].[USER_SESSION] ([UserID] ASC);
GO

CREATE NONCLUSTERED INDEX [IX_USAGE_UserMonth] ON [dbo].[USER_USAGE] ([UserID] ASC, [UsageMonth] ASC);
GO

CREATE NONCLUSTERED INDEX [IX_VT_ExpiresAt] ON [dbo].[VERIFICATION_TOKEN] ([ExpiresAt] ASC);
GO

CREATE NONCLUSTERED INDEX [IX_VT_Token] ON [dbo].[VERIFICATION_TOKEN] ([Token] ASC);
GO

CREATE NONCLUSTERED INDEX [IX_VT_UserID] ON [dbo].[VERIFICATION_TOKEN] ([UserID] ASC);
GO

-- View: Usage cua Academic User thang hien tai
CREATE   VIEW V_ACADEMIC_USAGE_CURRENT AS
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

CREATE   VIEW dbo.V_SYNC_HISTORY AS SELECT sl.LogID, sl.StartedAt, sl.CompletedAt, sl.SyncType, sl.IsManual, sl.Status, sl.PapersFetched, sl.PapersInserted, sl.PapersUpdated, sl.ErrorMessage, src.SourceName, DATEDIFF(SECOND, sl.StartedAt, sl.CompletedAt) AS DurationSeconds FROM dbo.SYNC_LOG sl JOIN dbo.API_SOURCE src ON sl.SourceID = src.SourceID;
GO

-- View: Top trending topics hien tai
CREATE   VIEW V_TRENDING_TOPICS AS
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

-- ============================================================
--  VIEWS
-- ============================================================

-- View: Thong tin day du user kem role name
CREATE   VIEW V_USER_DETAIL AS
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

-- SP: Don dep session het han (chay dinh ky boi Scheduler)
CREATE   PROCEDURE SP_CLEANUP_EXPIRED_SESSIONS
AS
BEGIN
    SET NOCOUNT ON;
    DELETE FROM USER_SESSION WHERE ExpiresAt < SYSDATETIME();
    SELECT @@ROWCOUNT AS [DeletedSessions];
END;
GO

-- ============================================================
--  STORED PROCEDURES
-- ============================================================

-- SP: Kiem tra va tang usage count cho Academic User
-- Tra ve 1 neu con trong gioi han, 0 neu da vuot
CREATE   PROCEDURE SP_CHECK_AND_INCREMENT_USAGE
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

-- SP: Cap nhat TrendScore va IsTrending (chay sau moi sync)
CREATE   PROCEDURE SP_REFRESH_TOPIC_TRENDS
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

-- SP: Reset usage hang thang cho tat ca Academic User
-- Chay vao 00:00 ngay 1 hang thang
CREATE   PROCEDURE SP_RESET_MONTHLY_USAGE
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

