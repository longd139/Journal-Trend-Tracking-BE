-- ============================================================
--  Migration: Add Type column to RESEARCH_PAPER
--  Purpose:  Store paper type from OpenAlex (article, dataset,
--            report, etc.) so we can filter out non-article junk
--            in author overview queries.
--  Run:      Execute this script against your SQL Server database
--            before restarting the application.
--  Date:     2026-07-09
-- ============================================================

-- 1. Add Type column if it doesn't already exist
IF OBJECT_ID('RESEARCH_PAPER', 'U') IS NOT NULL
   AND NOT EXISTS (
       SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
       WHERE TABLE_NAME = 'RESEARCH_PAPER' AND COLUMN_NAME = 'Type'
   )
BEGIN
    ALTER TABLE RESEARCH_PAPER ADD Type NVARCHAR(50) NULL
    PRINT 'Migration applied: RESEARCH_PAPER.Type column added.'
END
ELSE
BEGIN
    PRINT 'Skipped: RESEARCH_PAPER.Type column already exists.'
END

-- 2. Backfill existing papers — mark all papers without a type as 'article'
--    since they were synced before this field existed.
--    Remove this block if you want to keep NULLs and re-sync instead.
IF EXISTS (
    SELECT 1 FROM RESEARCH_PAPER WHERE Type IS NULL
)
BEGIN
    UPDATE RESEARCH_PAPER SET Type = 'article' WHERE Type IS NULL
    PRINT 'Backfill applied: Existing papers set to Type = ''article''.'
END
ELSE
BEGIN
    PRINT 'Skipped: No NULL Type values to backfill.'
END
