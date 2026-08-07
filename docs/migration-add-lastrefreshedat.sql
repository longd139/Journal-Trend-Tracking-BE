-- Migration: Add LastRefreshedAt column to SEARCH_KEYWORD
-- Purpose: Track when keyword data was last refreshed from OpenAlex
-- Used by: PaperSearchOrchestrator DB-based cache (7-day TTL)
-- Date: 2026-08-07

-- Check if column doesn't exist before adding
IF NOT EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = OBJECT_ID('SEARCH_KEYWORD')
    AND name = 'LastRefreshedAt'
)
BEGIN
    ALTER TABLE [dbo].[SEARCH_KEYWORD] ADD [LastRefreshedAt] datetime2(7) NULL;
    PRINT 'Added LastRefreshedAt column to SEARCH_KEYWORD';
END
ELSE
BEGIN
    PRINT 'LastRefreshedAt column already exists in SEARCH_KEYWORD';
END
GO