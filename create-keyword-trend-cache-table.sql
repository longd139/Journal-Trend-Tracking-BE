-- ============================================================
-- SCITRACK — Keyword Trend Report Cache Table
-- Stores generated keyword trend reports as JSON for history
-- and fast retrieval without recomputation.
-- Safe to re-run (uses IF NOT EXISTS).
-- ============================================================

IF NOT EXISTS (SELECT * FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'KEYWORD_TREND_CACHE')
BEGIN
    CREATE TABLE KEYWORD_TREND_CACHE (
        CacheID UNIQUEIDENTIFIER NOT NULL PRIMARY KEY DEFAULT NEWID(),
        Keyword NVARCHAR(500) NOT NULL,
        NormalizedKeyword NVARCHAR(500) NOT NULL UNIQUE,
        ReportData NVARCHAR(MAX) NOT NULL,
        CreatedAt DATETIME2 NOT NULL DEFAULT GETDATE(),
        UpdatedAt DATETIME2 NOT NULL DEFAULT GETDATE()
    );
    PRINT 'Table KEYWORD_TREND_CACHE created successfully.';
END
ELSE
BEGIN
    PRINT 'Table KEYWORD_TREND_CACHE already exists — skipped.';
END
GO
