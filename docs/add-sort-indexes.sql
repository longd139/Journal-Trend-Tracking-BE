-- Add indexes for search/sort performance
-- Run this once on the RESEARCH_PAPER table

-- Index for browse by year range (findByPubYearBetween)
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IDX_PAPER_PUBYEAR')
    CREATE INDEX IDX_PAPER_PUBYEAR ON RESEARCH_PAPER (PubYear);

-- Index for sort by created date
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IDX_PAPER_CREATEDAT')
    CREATE INDEX IDX_PAPER_CREATEDAT ON RESEARCH_PAPER (CreatedAt);

-- Index for sort by citation count
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IDX_PAPER_CITATIONS')
    CREATE INDEX IDX_PAPER_CITATIONS ON RESEARCH_PAPER (CitationCount);

-- Title index skipped: NVARCHAR(1000) exceeds 1700-byte nonclustered key limit
-- Title sorting will use full scan — acceptable for alphabetically sorted results
