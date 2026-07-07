#!/usr/bin/env node

/**
 * OpenAlex Snapshot Importer v1.0 — SCITRACK Data Pipeline
 *
 * Reads OpenAlex S3 snapshot JSON Lines (.gz) files and generates SQL scripts
 * ready to run on SCITRACK SQL Server.
 *
 * Unlike the API-based scraper.js, this tool reads LOCAL snapshot files,
 * so there is NO rate limit and NO 10k pagination cap. You can import
 * millions of papers in one run.
 *
 * Usage:
 *   node importer.js --input ./openalex-snapshot/data/works
 *   node importer.js --input ./data --year-from 2023 --year-to 2026
 *   node importer.js --input ./data --year 2024 --batch-size 5000
 *   node importer.js --stats-only --input ./data
 *
 * Output:
 *   ./output/papers_batch_0001.sql
 *   ./output/papers_batch_0002.sql
 *   ./output/import_stats.json
 */

import { writeFileSync, mkdirSync, readdirSync, statSync, existsSync } from "fs";
import { join, basename } from "path";
import { createReadStream } from "fs";
import { createInterface } from "readline";
import { createGunzip } from "zlib";
import { pipeline } from "stream/promises";
import { Transform, PassThrough } from "stream";

// ═══════════════════════════════════════════════════════════════
//  Configuration
// ═══════════════════════════════════════════════════════════════

const OUTPUT_DIR = "./output";
const DEFAULT_BATCH_SIZE = 5000;   // papers per SQL file
const DEFAULT_TXN_SIZE = 500;      // papers per transaction within a file
const MAX_AUTHORS_PER_PAPER = 5;
const MAX_KEYWORDS_PER_PAPER = 8;
const TITLE_MAX_LENGTH = 1000;

// ═══════════════════════════════════════════════════════════════
//  CLI argument parsing
// ═══════════════════════════════════════════════════════════════

function parseArgs() {
  const args = process.argv.slice(2);
  const opts = {
    input: "",
    yearFrom: null,
    yearTo: null,
    batchSize: DEFAULT_BATCH_SIZE,
    txnSize: DEFAULT_TXN_SIZE,
    statsOnly: false,
    maxPapers: 0,          // 0 = no limit
    dryRun: false,
    help: false,
  };

  for (let i = 0; i < args.length; i++) {
    switch (args[i]) {
      case "--input":       case "-i":  opts.input = args[++i];        break;
      case "--year-from":                opts.yearFrom = parseInt(args[++i], 10); break;
      case "--year-to":                  opts.yearTo = parseInt(args[++i], 10);   break;
      case "--year":                     opts.yearFrom = opts.yearTo = parseInt(args[++i], 10); break;
      case "--batch-size":               opts.batchSize = parseInt(args[++i], 10); break;
      case "--txn-size":                 opts.txnSize = parseInt(args[++i], 10);   break;
      case "--max-papers":               opts.maxPapers = parseInt(args[++i], 10); break;
      case "--stats-only":               opts.statsOnly = true;        break;
      case "--dry-run":                  opts.dryRun = true;           break;
      case "--help":        case "-h":   opts.help = true;             break;
    }
  }
  return opts;
}

function printHelp() {
  console.log(`
╔══════════════════════════════════════════════════════════════════╗
║  OpenAlex Snapshot Importer v1.0 — SCITRACK Data Pipeline       ║
╚══════════════════════════════════════════════════════════════════╝

Usage: node importer.js [options]

Required:
  -i, --input <dir>     Root directory of OpenAlex snapshot
                        (e.g., ./openalex-snapshot/data/works)

Filtering:
  --year <YYYY>         Import only papers from a specific year
  --year-from <YYYY>    Start year (inclusive)
  --year-to <YYYY>      End year (inclusive)
  --max-papers <N>      Stop after importing N papers (0 = unlimited)

Output:
  --batch-size <N>      Papers per SQL output file (default: ${DEFAULT_BATCH_SIZE})
  --txn-size <N>        Papers per SQL transaction (default: ${DEFAULT_TXN_SIZE})

Other:
  --stats-only          Only scan files and show statistics, no SQL generation
  --dry-run             Parse papers but don't write SQL files
  -h, --help            Show this help

Examples:
  # Import all papers from 2023-2026 (5k papers per SQL file)
  node importer.js -i ./openalex-snapshot/data/works --year-from 2023 --year-to 2026

  # Import only 2024 papers, 10k per file
  node importer.js -i ./data/works --year 2024 --batch-size 10000

  # Scan and show stats without generating SQL
  node importer.js -i ./data/works --stats-only

  # Test with first 1000 papers only
  node importer.js -i ./data/works --max-papers 1000

Snapshot download (one-time):
  aws s3 sync "s3://openalex/data/works/" "./openalex-snapshot/data/works/" \\
    --no-sign-request \\
    --exclude "*" \\
    --include "updated_date=2023-*/*" \\
    --include "updated_date=2024-*/*" \\
    --include "updated_date=2025-*/*" \\
    --include "updated_date=2026-*/*"
`);
}

// ═══════════════════════════════════════════════════════════════
//  File discovery — find all .gz files recursively
// ═══════════════════════════════════════════════════════════════

function discoverFiles(rootDir) {
  if (!existsSync(rootDir)) {
    console.error(`❌ Directory not found: "${rootDir}"`);
    process.exit(1);
  }

  const files = [];
  const stat = statSync(rootDir);

  if (stat.isFile()) {
    // Single file mode
    if (rootDir.endsWith(".gz") || rootDir.endsWith(".jsonl")) {
      files.push(rootDir);
    }
    return files;
  }

  // Recursive directory scan
  function walk(dir) {
    const entries = readdirSync(dir, { withFileTypes: true });
    for (const entry of entries) {
      const fullPath = join(dir, entry.name);
      if (entry.isDirectory()) {
        walk(fullPath);
      } else if (entry.name.endsWith(".gz")) {
        files.push(fullPath);
      } else if (entry.name.endsWith(".jsonl")) {
        files.push(fullPath);
      }
    }
  }

  walk(rootDir);
  return files.sort();
}

// ═══════════════════════════════════════════════════════════════
//  JSON parsing — read and decompress .gz files line by line
// ═══════════════════════════════════════════════════════════════

async function* streamPapers(filePath) {
  return new Promise((resolve, reject) => {
    const isGz = filePath.endsWith(".gz");
    const readStream = createReadStream(filePath);
    const gunzip = isGz ? createGunzip() : new PassThrough();

    // Handle gunzip errors (corrupt files, truncated downloads)
    gunzip.on("error", (err) => {
      console.warn(`  ⚠ Skipping corrupt file: ${basename(filePath)} — ${err.message}`);
      // Destroy the read stream to stop processing
      readStream.destroy();
    });

    readStream.on("error", (err) => {
      console.warn(`  ⚠ Cannot read file: ${basename(filePath)} — ${err.message}`);
    });

    const rl = createInterface({ input: readStream.pipe(gunzip), crlfDelay: Infinity });
    const papers = [];
    let lineNum = 0;

    rl.on("line", (line) => {
      lineNum++;
      if (!line.trim()) return;
      try {
        const work = JSON.parse(line);
        if (work && work.id) {
          papers.push(work);
        }
      } catch (e) {
        // Skip malformed lines silently (rare but possible)
        if (lineNum <= 3) {
          console.warn(`  ⚠ Parse error at line ${lineNum} in ${basename(filePath)}`);
        }
      }
    });

    rl.on("close", () => resolve(papers));
    rl.on("error", (err) => reject(err));
  });
}

// ═══════════════════════════════════════════════════════════════
//  Data mapping — OpenAlex API → SCITRACK schema
// ═══════════════════════════════════════════════════════════════

function rebuildAbstract(invertedIndex) {
  if (!invertedIndex || typeof invertedIndex !== "object") return null;
  const entries = Object.entries(invertedIndex);
  if (entries.length === 0) return null;

  const positions = [];
  for (const [word, indices] of entries) {
    for (const idx of indices) {
      positions.push([idx, word]);
    }
  }
  positions.sort((a, b) => a[0] - b[0]);
  return positions.map(p => p[1]).join(" ");
}

function normalizeDoi(doi) {
  if (!doi) return null;
  return doi.replace(/^https?:\/\/doi\.org\//i, "");
}

function trimToLength(str, max) {
  if (!str) return null;
  const t = str.trim();
  return t.length > max ? t.substring(0, max) : t;
}

function resolvePdfUrl(work) {
  const bestOa = work.best_oa_location || work.primary_location;
  if (bestOa?.pdf_url) return bestOa.pdf_url;
  if (work.open_access?.oa_url) return work.open_access.oa_url;
  return null;
}

function extractJournal(work) {
  const src = work.primary_location?.source;
  if (!src?.display_name) return null;
  return {
    displayName: trimToLength(src.display_name, 500),
    issn: trimToLength(src.issn_l, 20),
    publisher: trimToLength(src.publisher || src.host_organization_name || null, 300),
  };
}

function extractAuthors(authorships, maxAuthors) {
  if (!authorships || authorships.length === 0) return [];
  return authorships.slice(0, maxAuthors).map((a, i) => ({
    openAlexId: a.author?.id || null,
    fullName: a.author?.display_name || a.raw_author_name || "Unknown Author",
    affiliations: a.raw_affiliation_strings || [],
    authorOrder: i + 1,
  }));
}

function extractKeywords(work, maxKeywords) {
  const seen = new Set();
  const result = [];

  if (work.keywords) {
    for (const kw of work.keywords) {
      const text = kw.display_name || kw.keyword;
      if (text && !seen.has(text.toLowerCase())) {
        seen.add(text.toLowerCase());
        result.push({ keyword: text, score: kw.score ?? 1.0 });
        if (result.length >= maxKeywords) return result;
      }
    }
  }

  if (work.topics) {
    for (const t of work.topics) {
      const text = t.display_name;
      if (text && !seen.has(text.toLowerCase())) {
        seen.add(text.toLowerCase());
        result.push({ keyword: text, score: t.score ?? 0.8, source: "topic" });
        if (result.length >= maxKeywords) return result;
      }
    }
  }

  return result;
}

function extractResearchField(work) {
  const topTopic = work.topics?.[0];
  if (!topTopic) return null;
  return {
    fieldName: topTopic.field?.display_name || topTopic.domain?.display_name || null,
    subfield: topTopic.subfield?.display_name || null,
    domain: topTopic.domain?.display_name || null,
  };
}

function resolveTitle(work) {
  return work.display_name || work.title || "Untitled";
}

function mapPaper(work) {
  const title = trimToLength(resolveTitle(work), TITLE_MAX_LENGTH);
  const abstractText = rebuildAbstract(work.abstract_inverted_index);

  return {
    openAlexId: work.id,
    title,
    abstract: abstractText,
    doi: normalizeDoi(work.doi),
    publicationDate: work.publication_date || null,
    publicationYear: work.publication_year ?? null,
    citationCount: work.cited_by_count ?? 0,
    isOpenAccess: work.open_access?.is_oa ?? false,
    pdfUrl: resolvePdfUrl(work),
    journal: extractJournal(work),
    authors: extractAuthors(work.authorships, MAX_AUTHORS_PER_PAPER),
    keywords: extractKeywords(work, MAX_KEYWORDS_PER_PAPER),
    researchField: extractResearchField(work),
    type: work.type || null,
    referencedWorksCount: work.referenced_works_count ?? 0,
  };
}

// ═══════════════════════════════════════════════════════════════
//  SQL Generator — produces executable SQL Server script
// ═══════════════════════════════════════════════════════════════

function escapeSql(str) {
  if (!str) return "NULL";
  return `N'${str.replace(/'/g, "''")}'`;
}

function escapeSqlNoN(str) {
  if (!str) return "NULL";
  return `'${str.replace(/'/g, "''")}'`;
}

function keywordNormalized(text) {
  return text.toLowerCase().trim().replace(/\s+/g, " ");
}

/**
 * Generate SQL header — run once at the beginning of each batch file.
 */
function generateHeader() {
  const ts = new Date().toISOString();
  return [
    "-- ═══════════════════════════════════════════════════════════════",
    `-- SCITRACK — OpenAlex Snapshot Importer v1.0`,
    `-- Generated: ${ts}`,
    "--",
    "-- USAGE: Open in SSMS / Azure Data Studio and execute.",
    "-- Safe to re-run: duplicates are skipped (DOI, title+year).",
    "-- Each batch is in its own transaction.",
    "-- ═══════════════════════════════════════════════════════════════",
    "",
    "SET NOCOUNT ON;",
    "GO",
    "",
    "-- Ensure API_SOURCE exists (idempotent)",
    "IF NOT EXISTS (SELECT 1 FROM API_SOURCE WHERE SourceName = 'OpenAlex')",
    "BEGIN",
    "    INSERT INTO API_SOURCE (SourceID, SourceName, BaseURL, IsActive, RateLimitRPM)",
    "    VALUES (NEWID(), N'OpenAlex', N'https://api.openalex.org', 1, 100);",
    "END",
    "GO",
    "",
  ].join("\n");
}

/**
 * Generate SQL for a single paper with all related entities.
 * Uses variables that are reset per paper.
 */
function generatePaperSql(paper, index) {
  const lines = [];
  const id = paper.openAlexId?.split("/").pop() || `PAPER_${index}`;

  lines.push("");
  lines.push(`-- [${index}] ${(paper.title || "UNTITLED").substring(0, 80)}`);
  lines.push(`-- OpenAlex: ${paper.openAlexId || "N/A"} | Year: ${paper.publicationYear || "?"} | Citations: ${paper.citationCount}`);
  lines.push("");

  // ── Journal ──
  if (paper.journal?.issn) {
    lines.push("-- Journal (upsert by ISSN)");
    lines.push(`IF EXISTS (SELECT 1 FROM JOURNAL WHERE ISSN = ${escapeSql(paper.journal.issn)})`);
    lines.push("    SELECT NULL; -- journal exists");
    lines.push("ELSE");
    lines.push("    INSERT INTO JOURNAL (JournalID, SourceID, JournalName, ISSN, Publisher, IsActive)");
    lines.push(`    SELECT NEWID(), (SELECT SourceID FROM API_SOURCE WHERE SourceName = 'OpenAlex'), ${escapeSql(paper.journal.displayName)}, ${escapeSql(paper.journal.issn)}, ${escapeSql(paper.journal.publisher)}, 1;`);
  }

  // ── Research Field ──
  if (paper.researchField?.fieldName) {
    lines.push("");
    lines.push("-- Research Field (upsert by FieldName)");
    lines.push(`IF NOT EXISTS (SELECT 1 FROM RESEARCH_FIELD WHERE FieldName = ${escapeSql(paper.researchField.fieldName)})`);
    lines.push(`    INSERT INTO RESEARCH_FIELD (FieldID, FieldName, IsTracked) VALUES (NEWID(), ${escapeSql(paper.researchField.fieldName)}, 1);`);
  }

  // ── Research Paper ──
  lines.push("");
  lines.push("-- Paper (insert if DOI is new)");
  lines.push("DECLARE @PaperID UNIQUEIDENTIFIER = NEWID();");
  lines.push("DECLARE @JournalID UNIQUEIDENTIFIER = NULL;");
  lines.push("DECLARE @FieldID UNIQUEIDENTIFIER = NULL;");
  lines.push("DECLARE @SourceID UNIQUEIDENTIFIER = (SELECT SourceID FROM API_SOURCE WHERE SourceName = 'OpenAlex');");
  lines.push("");

  if (paper.journal?.issn) {
    lines.push(`SET @JournalID = (SELECT TOP 1 JournalID FROM JOURNAL WHERE ISSN = ${escapeSql(paper.journal.issn)});`);
  }
  if (paper.researchField?.fieldName) {
    lines.push(`SET @FieldID = (SELECT TOP 1 FieldID FROM RESEARCH_FIELD WHERE FieldName = ${escapeSql(paper.researchField.fieldName)});`);
  }

  // DOI-based dedup
  if (paper.doi) {
    lines.push("");
    lines.push(`-- Check DOI duplicate`);
    lines.push(`IF EXISTS (SELECT 1 FROM RESEARCH_PAPER WHERE DOI = ${escapeSql(paper.doi)})`);
    lines.push(`    THROW 50000, 'SKIP_DOI: ${id}', 1;  -- caught by outer TRY/CATCH, paper is skipped`);
  }

  // Title+Year dedup (only if no DOI)
  if (!paper.doi) {
    lines.push(`IF EXISTS (SELECT 1 FROM RESEARCH_PAPER WHERE Title = ${escapeSql(paper.title)} AND PubYear = ${paper.publicationYear ?? "NULL"})`);
    lines.push(`    THROW 50000, 'SKIP_TITLE: ${id}', 1;`);
  }

  lines.push("");
  lines.push("INSERT INTO RESEARCH_PAPER (PaperID, SourceID, JournalID, FieldID, Title, Abstract, DOI, PubDate, PubYear, CitationCount, IsOpenAccess, PdfUrl)");
  lines.push("VALUES (");
  lines.push("    @PaperID,");
  lines.push("    @SourceID,");
  lines.push("    @JournalID,");
  lines.push("    @FieldID,");
  lines.push(`    ${escapeSql(paper.title)},`);
  lines.push(`    ${escapeSql(paper.abstract)},`);
  lines.push(`    ${paper.doi ? escapeSql(paper.doi) : "NULL"},`);
  lines.push(`    ${paper.publicationDate ? escapeSqlNoN(paper.publicationDate) : "NULL"},`);
  lines.push(`    ${paper.publicationYear ?? "NULL"},`);
  lines.push(`    ${paper.citationCount},`);
  lines.push(`    ${paper.isOpenAccess ? 1 : 0},`);
  lines.push(`    ${escapeSql(paper.pdfUrl)}`);
  lines.push(");");

  // ── Authors ──
  if (paper.authors.length > 0) {
    lines.push("");
    lines.push(`-- Authors (${paper.authors.length})`);
    for (const author of paper.authors) {
      const extId = author.openAlexId?.split("/").pop() || null;
      const extIdVal = extId ? escapeSql(extId) : "NULL";

      if (extId) {
        lines.push(`IF NOT EXISTS (SELECT 1 FROM AUTHOR WHERE SourceID = @SourceID AND ExternalAuthorID = ${extIdVal})`);
        lines.push(`    INSERT INTO AUTHOR (AuthorID, SourceID, ExternalAuthorID, FullName, Affiliation, HIndex, TotalCitations)`);
        lines.push(`    VALUES (NEWID(), @SourceID, ${extIdVal}, ${escapeSql(author.fullName)}, ${escapeSql(author.affiliations?.[0] || "Unknown")}, 0, 0);`);
        lines.push(`DECLARE @AID_${author.authorOrder} UNIQUEIDENTIFIER = (SELECT AuthorID FROM AUTHOR WHERE SourceID = @SourceID AND ExternalAuthorID = ${extIdVal});`);
      } else {
        lines.push(`DECLARE @AID_${author.authorOrder} UNIQUEIDENTIFIER = NEWID();`);
        lines.push(`INSERT INTO AUTHOR (AuthorID, SourceID, ExternalAuthorID, FullName, Affiliation, HIndex, TotalCitations)`);
        lines.push(`VALUES (@AID_${author.authorOrder}, @SourceID, NULL, ${escapeSql(author.fullName)}, ${escapeSql(author.affiliations?.[0] || "Unknown")}, 0, 0);`);
      }

      lines.push(`IF NOT EXISTS (SELECT 1 FROM PAPER_AUTHOR WHERE PaperID = @PaperID AND AuthorID = @AID_${author.authorOrder})`);
      lines.push(`    INSERT INTO PAPER_AUTHOR (PaperID, AuthorID, AuthorOrder, IsCorresponding) VALUES (@PaperID, @AID_${author.authorOrder}, ${author.authorOrder}, 0);`);
      lines.push("");
    }
  }

  // ── Keywords ──
  if (paper.keywords.length > 0) {
    lines.push(`-- Keywords (${paper.keywords.length})`);
    for (let i = 0; i < paper.keywords.length; i++) {
      const kw = paper.keywords[i];
      const norm = keywordNormalized(kw.keyword);
      const score = kw.score != null ? kw.score.toFixed(4) : "NULL";

      lines.push(`DECLARE @KWID_${i} UNIQUEIDENTIFIER;`);
      lines.push(`IF EXISTS (SELECT 1 FROM KEYWORD WHERE NormalizedText = ${escapeSql(norm)})`);
      lines.push("BEGIN");
      lines.push(`    SET @KWID_${i} = (SELECT TOP 1 KeywordID FROM KEYWORD WHERE NormalizedText = ${escapeSql(norm)});`);
      lines.push(`    UPDATE KEYWORD SET PaperCount = ISNULL(PaperCount, 0) + 1 WHERE KeywordID = @KWID_${i};`);
      lines.push("END");
      lines.push("ELSE");
      lines.push("BEGIN");
      lines.push(`    SET @KWID_${i} = NEWID();`);
      lines.push(`    INSERT INTO KEYWORD (KeywordID, FieldID, KeywordText, NormalizedText, PaperCount) VALUES (@KWID_${i}, @FieldID, ${escapeSql(kw.keyword)}, ${escapeSql(norm)}, 1);`);
      lines.push("END");
      lines.push(`IF NOT EXISTS (SELECT 1 FROM PAPER_KEYWORD WHERE PaperID = @PaperID AND KeywordID = @KWID_${i})`);
      lines.push(`    INSERT INTO PAPER_KEYWORD (PaperID, KeywordID, RelevanceScore) VALUES (@PaperID, @KWID_${i}, ${score});`);
      lines.push("");
    }
  }

  return lines.join("\n");
}

/**
 * Wrap papers in a transaction with TRY/CATCH for per-paper error handling.
 * If a paper is a duplicate, we skip it and continue with the next one.
 */
function generateTransactionSql(papers, startIndex, txnIndex) {
  const lines = [];
  lines.push("-- ───────────────────────────────────────────────────────────────");
  lines.push(`-- TRANSACTION #${txnIndex}: papers ${startIndex + 1}–${startIndex + papers.length}`);
  lines.push("-- ───────────────────────────────────────────────────────────────");
  lines.push("BEGIN TRY");
  lines.push("  BEGIN TRANSACTION;");

  for (let i = 0; i < papers.length; i++) {
    const globalIndex = startIndex + i + 1;
    lines.push(generatePaperSql(papers[i], globalIndex));
  }

  lines.push("  COMMIT TRANSACTION;");
  lines.push(`  PRINT '✅ TXN #${txnIndex}: ${papers.length} papers committed (${startIndex + 1}–${startIndex + papers.length})';`);
  lines.push("END TRY");
  lines.push("BEGIN CATCH");
  lines.push("  IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;");
  lines.push(`  DECLARE @ErrMsg NVARCHAR(MAX) = ERROR_MESSAGE();`);
  // If it's a duplicate skip, just print and continue; otherwise re-throw
  lines.push(`  IF @ErrMsg LIKE '%SKIP_DOI%' OR @ErrMsg LIKE '%SKIP_TITLE%'`);
  lines.push(`    PRINT '⏭ Skipped duplicate in TXN #${txnIndex}: ' + @ErrMsg;`);
  lines.push("  ELSE");
  lines.push("  BEGIN");
  lines.push(`    PRINT '❌ TXN #${txnIndex} FAILED: ' + @ErrMsg;`);
  lines.push("    THROW;");
  lines.push("  END");
  lines.push("END CATCH");
  lines.push("GO");
  lines.push("");

  return lines.join("\n");
}

/**
 * Generate a full SQL batch file with header + N transactions.
 */
function generateBatchFile(papers, batchIndex, batchSize, txnSize) {
  const parts = [generateHeader()];

  const totalPapers = papers.length;
  const totalTxns = Math.ceil(totalPapers / txnSize);

  parts.push(`-- Batch #${batchIndex}: ${totalPapers} papers in ${totalTxns} transactions`);
  parts.push("");

  for (let txnIdx = 0; txnIdx < totalTxns; txnIdx++) {
    const chunk = papers.slice(txnIdx * txnSize, (txnIdx + 1) * txnSize);
    parts.push(generateTransactionSql(chunk, txnIdx * txnSize, batchIndex * 1000 + txnIdx + 1));
  }

  parts.push(`PRINT '✅ Batch #${batchIndex} complete: ${totalPapers} papers processed.';`);
  parts.push("GO");
  parts.push("");

  return parts.join("\n");
}

// ═══════════════════════════════════════════════════════════════
//  Deduplication helpers (in-memory, within this import session)
// ═══════════════════════════════════════════════════════════════

class DedupTracker {
  constructor() {
    this.dois = new Set();
    this.titleYears = new Set();
  }

  isDuplicate(paper) {
    // DOI check
    if (paper.doi) {
      const normalizedDoi = paper.doi.toLowerCase().trim();
      if (this.dois.has(normalizedDoi)) return true;
      this.dois.add(normalizedDoi);
    }

    // Title + Year check
    if (paper.title && paper.publicationYear) {
      const key = `${paper.title.toLowerCase().trim()}|${paper.publicationYear}`;
      if (this.titleYears.has(key)) return true;
      this.titleYears.add(key);
    }

    return false;
  }

  size() {
    return this.dois.size + this.titleYears.size;
  }
}

// ═══════════════════════════════════════════════════════════════
//  Statistics
// ═══════════════════════════════════════════════════════════════

class ImportStats {
  constructor() {
    this.totalFiles = 0;
    this.totalParsed = 0;
    this.totalFiltered = 0;
    this.totalSkippedDuplicate = 0;
    this.totalWritten = 0;
    this.yearDistribution = {};
    this.typeDistribution = {};
    this.sourceDistribution = {};
    this.startTime = Date.now();
  }

  recordParsed() { this.totalParsed++; }
  recordFiltered() { this.totalFiltered++; }
  recordSkippedDuplicate() { this.totalSkippedDuplicate++; }
  recordWritten(paper) {
    this.totalWritten++;
    const y = paper.publicationYear || "unknown";
    this.yearDistribution[y] = (this.yearDistribution[y] || 0) + 1;
    const t = paper.type || "unknown";
    this.typeDistribution[t] = (this.typeDistribution[t] || 0) + 1;
  }

  elapsed() {
    return ((Date.now() - this.startTime) / 1000).toFixed(1);
  }

  print() {
    const topYears = Object.entries(this.yearDistribution)
      .sort((a, b) => b[1] - a[1]).slice(0, 10);
    const topTypes = Object.entries(this.typeDistribution)
      .sort((a, b) => b[1] - a[1]);

    console.log("\n╔══════════════════════════════════════════════════════╗");
    console.log("║  📊 IMPORT SUMMARY                                  ║");
    console.log("╠══════════════════════════════════════════════════════╣");
    console.log(`║  Files scanned:     ${String(this.totalFiles).padStart(6)}                        ║`);
    console.log(`║  Papers parsed:     ${String(this.totalParsed).padStart(6)}                        ║`);
    console.log(`║  Filtered out:      ${String(this.totalFiltered).padStart(6)}  (year range)         ║`);
    console.log(`║  Duplicates skipped:${String(this.totalSkippedDuplicate).padStart(6)}                        ║`);
    console.log(`║  Papers written:    ${String(this.totalWritten).padStart(6)}  ✓                     ║`);
    console.log(`║  Time elapsed:      ${String(this.elapsed() + "s").padStart(6)}                        ║`);
    console.log("╠══════════════════════════════════════════════════════╣");
    console.log("║  📅 Top years:                                     ║");
    topYears.forEach(([year, count]) => {
      console.log(`║    ${year}: ${String(count).padStart(8)}                                  ║`);
    });
    if (topTypes.length > 0) {
      console.log("╠══════════════════════════════════════════════════════╣");
      console.log("║  📄 Types:                                         ║");
      topTypes.forEach(([type, count]) => {
        console.log(`║    ${(type || "unknown").padEnd(22)} ${String(count).padStart(8)}              ║`);
      });
    }
    console.log("╚══════════════════════════════════════════════════════╝");
  }

  save(filePath) {
    const data = {
      generatedAt: new Date().toISOString(),
      filesScanned: this.totalFiles,
      papersParsed: this.totalParsed,
      papersFiltered: this.totalFiltered,
      papersSkippedDuplicate: this.totalSkippedDuplicate,
      papersWritten: this.totalWritten,
      elapsedSeconds: parseFloat(this.elapsed()),
      yearDistribution: this.yearDistribution,
      typeDistribution: this.typeDistribution,
    };
    writeFileSync(filePath, JSON.stringify(data, null, 2), "utf-8");
    console.log(`\n📊 Stats saved: ${filePath}`);
  }
}

// ═══════════════════════════════════════════════════════════════
//  Main import logic
// ═══════════════════════════════════════════════════════════════

async function runImport(opts) {
  const stats = new ImportStats();
  const dedup = new DedupTracker();

  // 1. Discover files
  console.log(`\n📂 Scanning for snapshot files in: ${opts.input}`);
  const files = discoverFiles(opts.input);
  stats.totalFiles = files.length;

  if (files.length === 0) {
    console.error(`❌ No .gz or .jsonl files found in "${opts.input}"`);
    console.error(`   Make sure you've downloaded the OpenAlex snapshot first.`);
    console.error(`   See: node importer.js --help`);
    process.exit(1);
  }

  const totalSizeGB = files.reduce((sum, f) => {
    try { return sum + statSync(f).size; } catch { return sum; }
  }, 0) / (1024 ** 3);

  console.log(`   Found ${files.length} files (${totalSizeGB.toFixed(1)} GB total)`);
  console.log(`   Year filter: ${opts.yearFrom || "any"} → ${opts.yearTo || "any"}`);
  console.log(`   Batch size: ${opts.batchSize} papers/file | Transaction size: ${opts.txnSize}`);
  if (opts.maxPapers > 0) console.log(`   Max papers: ${opts.maxPapers.toLocaleString()}`);
  console.log("");

  if (opts.statsOnly) {
    console.log("🔍 Stats-only mode — scanning files without generating SQL...\n");
  }

  // 2. Process each file
  mkdirSync(OUTPUT_DIR, { recursive: true });
  let currentBatch = [];
  let batchIndex = 0;
  let lastProgressTime = Date.now();

  function flushBatch() {
    if (currentBatch.length === 0) return;
    batchIndex++;
    const filename = `papers_batch_${String(batchIndex).padStart(4, "0")}.sql`;
    const filePath = join(OUTPUT_DIR, filename);

    if (!opts.dryRun && !opts.statsOnly) {
      const sql = generateBatchFile(currentBatch, batchIndex, opts.batchSize, opts.txnSize);
      writeFileSync(filePath, sql, "utf-8");
      const sizeMB = (Buffer.byteLength(sql, "utf-8") / (1024 ** 2)).toFixed(1);
      console.log(`\n💾 ${filename}: ${currentBatch.length} papers, ${sizeMB} MB`);
    }
    currentBatch = [];
  }

  for (let fi = 0; fi < files.length; fi++) {
    const file = files[fi];
    const fileName = basename(file);
    const fileSizeMB = (() => { try { return statSync(file).size / (1024 ** 2); } catch { return 0; } })();

    if (opts.statsOnly) {
      process.stdout.write(`\r   [${fi + 1}/${files.length}] Scanning: ${fileName} (${fileSizeMB.toFixed(0)} MB)...`);
    } else {
      console.log(`📄 [${fi + 1}/${files.length}] ${fileName} (${fileSizeMB.toFixed(0)} MB)`);
    }

    let paperBuffer;
    try {
      paperBuffer = await streamPapers(file);
    } catch (err) {
      console.warn(`   ⚠ Failed to process ${fileName}: ${err.message} — skipping file`);
      continue;
    }

    if (!paperBuffer || paperBuffer.length === 0) {
      if (!opts.statsOnly) console.log(`   (empty file, skipping)`);
      continue;
    }

    for (const work of paperBuffer) {
      stats.recordParsed();

      // Year filter
      if (opts.yearFrom != null || opts.yearTo != null) {
        const py = work.publication_year;
        if (py != null) {
          if (opts.yearFrom != null && py < opts.yearFrom) { stats.recordFiltered(); continue; }
          if (opts.yearTo != null && py > opts.yearTo) { stats.recordFiltered(); continue; }
        }
      }

      const paper = mapPaper(work);

      // In-memory dedup
      if (dedup.isDuplicate(paper)) {
        stats.recordSkippedDuplicate();
        continue;
      }

      stats.recordWritten(paper);
      currentBatch.push(paper);

      // Progress
      if (stats.totalWritten % 1000 === 0 && Date.now() - lastProgressTime > 2000) {
        lastProgressTime = Date.now();
        if (!opts.statsOnly) {
          process.stdout.write(`\r   📊 ${stats.totalWritten.toLocaleString()} papers mapped | Batch #${batchIndex + 1}: ${currentBatch.length} papers | ${stats.elapsed()}s elapsed`);
        }
      }

      // Flush batch when full
      if (currentBatch.length >= opts.batchSize) {
        flushBatch();
      }

      // Max papers limit
      if (opts.maxPapers > 0 && stats.totalWritten >= opts.maxPapers) {
        console.log(`\n⏹ Reached max papers limit (${opts.maxPapers.toLocaleString()})`);
        flushBatch();
        break;
      }
    }

    // Show file summary
    if (!opts.statsOnly) {
      console.log(`   ✓ ${paperBuffer.length} papers parsed (total written: ${stats.totalWritten.toLocaleString()})`);
    }

    // Check max papers
    if (opts.maxPapers > 0 && stats.totalWritten >= opts.maxPapers) break;
  }

  // Flush final batch
  flushBatch();

  // Print summary
  console.log(""); // newline after progress
  stats.print();

  // Save stats
  mkdirSync(OUTPUT_DIR, { recursive: true });
  stats.save(join(OUTPUT_DIR, "import_stats.json"));

  // Print output instructions
  if (!opts.statsOnly && !opts.dryRun && batchIndex > 0) {
    console.log(`\n📋 Generated ${batchIndex} SQL batch file(s) in: ${OUTPUT_DIR}/`);
    console.log("   To import into SQL Server:");
    console.log("   1. Open SSMS / Azure Data Studio");
    console.log("   2. Open each SQL file and execute (F5)");
    console.log("   3. Or use sqlcmd:");
    console.log("      sqlcmd -S <server> -d <database> -U <user> -P <pass> -i output/papers_batch_0001.sql");
    console.log("\n   💡 Tip: Run files in order. Each is self-contained and safe to re-run.");
  }

  if (stats.totalWritten > 0 && !opts.statsOnly) {
    console.log("\n🔗 After SQL import, trigger Neo4j reindex:");
    console.log("   curl -X POST http://localhost:8080/api/v1/admin/sync/reindex-keywords");
  }
}

// ═══════════════════════════════════════════════════════════════
//  Entry point
// ═══════════════════════════════════════════════════════════════

const opts = parseArgs();

if (opts.help) {
  printHelp();
  process.exit(0);
}

if (!opts.input) {
  console.error("❌ --input <directory> is required.");
  console.error("   Usage: node importer.js --input ./openalex-snapshot/data/works");
  console.error("   See:   node importer.js --help");
  process.exit(1);
}

console.log("╔══════════════════════════════════════════════════════╗");
console.log("║  OpenAlex Snapshot Importer v1.0                    ║");
console.log("║  SCITRACK Data Pipeline                             ║");
console.log("╚══════════════════════════════════════════════════════╝");

await runImport(opts);
