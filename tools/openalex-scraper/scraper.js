#!/usr/bin/env node

/**
 * OpenAlex Scraper — Standalone tool for SCITRACK system.
 *
 * Scrapes data from the OpenAlex API and outputs:
 *   - JSON files (default)
 *   - SQL files (--format sql) ready to run on SCITRACK SQL Server
 *
 * Usage:
 *   # Search by keyword → JSON
 *   node scraper.js --entity papers --query "machine learning" --limit 50
 *
 *   # Search by keyword → SQL
 *   node scraper.js --entity papers --query "machine learning" --limit 50 --format sql
 *
 *   # BATCH MODE — fetch multiple papers by OpenAlex ID/URL at once → SQL
 *   node scraper.js --entity batch --ids "W4292779060,W4302570518"
 *   node scraper.js --entity batch --ids "https://openalex.org/works/W4292779060,W4302570518"
 *   node scraper.js --entity batch --ids-file urls.txt --format sql
 *
 * Output:
 *   ./output/papers_<timestamp>.json                (JSON mode)
 *   ./output/papers_<timestamp>.sql                 (SQL mode)
 *   ./output/batch_papers_<timestamp>.sql
 */

import { writeFileSync, mkdirSync, readFileSync, existsSync } from "fs";
import { createInterface } from "readline";

const BASE_URL = "https://api.openalex.org";
const DEFAULT_LIMIT = 500;
const MAX_PER_PAGE = 200;
const OPENALEX_PAGINATION_CAP = 10000;
const OUTPUT_DIR = "./output";
const BATCH_CHUNK_SIZE = 50;

// ── CLI argument parsing ────────────────────────────────────────────

function parseArgs() {
  const args = process.argv.slice(2);
  const opts = {
    entity: "all",
    limit: DEFAULT_LIMIT,
    query: "",
    output: null,
    format: "json",     // "json" | "sql"
    mailto: "",         // polite pool email (optional)
    ids: "",            // comma-separated IDs or URLs (for batch mode)
    idsFile: "",        // file path with one ID/URL per line
  };
  for (let i = 0; i < args.length; i++) {
    switch (args[i]) {
      case "--entity":   case "-e":  opts.entity = args[++i]; break;
      case "--query":    case "-q":  opts.query = args[++i];  break;
      case "--limit":    case "-l":  opts.limit = parseInt(args[++i], 10); break;
      case "--output":   case "-o":  opts.output = args[++i]; break;
      case "--format":   case "-f":  opts.format = args[++i]; break;
      case "--mailto":   case "-m":  opts.mailto = args[++i]; break;
      case "--ids":                   opts.ids = args[++i];     break;
      case "--ids-file":              opts.idsFile = args[++i]; break;
      case "--help":     case "-h":  printHelp(); process.exit(0);
    }
  }

  // Validate email — REQUIRED for polite pool (100k req/day vs ~10 req/s public pool)
  if (!opts.mailto || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(opts.mailto)) {
    console.error(`Error: --mailto <your@email.com> is REQUIRED.`);
    console.error(`  OpenAlex polite pool gives you 100k requests/day.`);
    console.error(`  Without it, you'll get rate-limited very quickly (~10 req/s public pool).`);
    console.error(`  Provide your email via --mailto or -m flag.`);
    process.exit(1);
  }

  if (!["papers", "authors", "journals", "all", "batch"].includes(opts.entity)) {
    console.error(`Error: unknown entity "${opts.entity}". Use: papers | authors | journals | all | batch`);
    process.exit(1);
  }
  if (!["json", "sql"].includes(opts.format)) {
    console.error(`Error: unknown format "${opts.format}". Use: json | sql`);
    process.exit(1);
  }
  if (opts.entity !== "batch" && !opts.query) {
    console.error("Error: --query is required (unless using --entity batch)");
    process.exit(1);
  }
  if (opts.entity === "batch" && !opts.ids && !opts.idsFile) {
    console.error("Error: --ids or --ids-file is required for batch mode");
    process.exit(1);
  }
  return opts;
}

function printHelp() {
  console.log(`
OpenAlex Scraper v2.0 — SCITRACK Data Pipeline
Usage: node scraper.js [options]

Options:
  -e, --entity    Entity to scrape: papers | authors | journals | all | batch (default: all)
  -q, --query     Search keyword (required, except batch mode)
  -l, --limit     Max results (default: ${DEFAULT_LIMIT}, max per page: ${MAX_PER_PAGE}, 0 = fetch ALL up to 10k)
  -f, --format    Output format: json | sql (default: json)
  -m, --mailto    Your email (REQUIRED) for OpenAlex polite pool (100k req/day vs public ~10 req/s)
  --ids           Comma-separated OpenAlex IDs or URLs (for batch mode)
  --ids-file      File path with one OpenAlex ID/URL per line (for batch mode)
  -o, --output    Output directory (default: ./output)
  -h, --help      Show this help

Examples:
  node scraper.js -e papers -q "machine learning" -l 50 -m your@email.com
  node scraper.js -e papers -q "machine learning" -l 0 -f sql -m your@email.com   # ALL papers!
  node scraper.js -e papers -q "deep learning" -l 100 -f sql
  node scraper.js -e batch --ids "W4292779060,W4302570518" -f sql -m your@email.com
  node scraper.js -e batch --ids-file my_links.txt -f sql -m your@email.com
`);
}

// ── Parse OpenAlex IDs from various formats ──────────────────────────

function parseOpenAlexIds(raw) {
  // Accepts: "W4292779060", "https://openalex.org/works/W4292779060", "openalex.org/W4292779060"
  // Also accepts full API URLs: "https://api.openalex.org/works/W4292779060"
  const ids = [];
  for (const part of raw.split(/[\n,;|]+/)) {
    const trimmed = part.trim();
    if (!trimmed) continue;
    // Extract the ID portion (Axxx for authors, Wxxx for works, Sxxx for sources)
    const match = trimmed.match(/([AWS]\d{7,})/i);
    if (match) {
      ids.push(match[1].toUpperCase());
    } else {
      console.warn(`  ⚠ Skipping unrecognized input: "${trimmed}"`);
    }
  }
  return [...new Set(ids)]; // deduplicate
}

function loadIdsFromFile(filePath) {
  if (!existsSync(filePath)) {
    console.error(`Error: file not found: "${filePath}"`);
    process.exit(1);
  }
  return parseOpenAlexIds(readFileSync(filePath, "utf-8"));
}

// ── HTTP helper with retry and jitter ──────────────────────────────

/**
 * Random delay between min and max milliseconds (inclusive).
 * Adds jitter to avoid thundering herd when multiple instances run.
 */
function randomDelay(minMs, maxMs) {
  return Math.floor(Math.random() * (maxMs - minMs + 1)) + minMs;
}

function sleep(ms) { return new Promise(r => setTimeout(r, ms)); }

async function fetchWithRetry(url, retries = 5) {
  for (let attempt = 0; attempt < retries; attempt++) {
    try {
      const res = await fetch(url);
      if (!res.ok) {
        const body = await res.text();
        if (res.status === 429) {
          // Read Retry-After header if available
          const retryAfter = res.headers.get("Retry-After");
          let waitSec;
          if (retryAfter) {
            waitSec = parseInt(retryAfter, 10) || 10;
          } else {
            // Exponential backoff with jitter: 10s → 20s → 40s → 80s → 160s
            waitSec = Math.min(10 * Math.pow(2, attempt), 180);
            // Add ±25% jitter
            waitSec = Math.floor(waitSec * (0.75 + Math.random() * 0.5));
          }
          console.warn(`  ⚠ 429 Rate limited (attempt ${attempt + 1}/${retries}) — waiting ${waitSec}s...`);
          await sleep(waitSec * 1000);
          continue;
        }
        if (res.status === 504) {
          const wait = randomDelay(3000, 8000);
          console.warn(`  ⚠ 504 Gateway Timeout (attempt ${attempt + 1}/${retries}) — retrying in ${(wait / 1000).toFixed(1)}s...`);
          await sleep(wait);
          continue;
        }
        if (res.status === 503) {
          const wait = randomDelay(5000, 15000);
          console.warn(`  ⚠ 503 Service Unavailable (attempt ${attempt + 1}/${retries}) — retrying in ${(wait / 1000).toFixed(1)}s...`);
          await sleep(wait);
          continue;
        }
        throw new Error(`HTTP ${res.status}: ${body.substring(0, 300)}`);
      }
      return await res.json();
    } catch (e) {
      if (attempt < retries - 1) {
        const wait = randomDelay(2000, 5000);
        console.warn(`  ⚠ Fetch failed (attempt ${attempt + 1}/${retries}): ${e.message} — retrying in ${(wait / 1000).toFixed(1)}s...`);
        await sleep(wait);
      } else {
        console.error(`  ✗ All ${retries} attempts failed: ${e.message}`);
        return null;
      }
    }
  }
}

// ── URL builders ─────────────────────────────────────────────────────

function buildWorksUrl(query, limit, page, mailto) {
  const perPage = Math.min(limit, MAX_PER_PAGE);
  const params = new URLSearchParams({
    search: query,
    "per-page": perPage.toString(),
    page: (page || 1).toString(),
    sort: "cited_by_count:desc",
    select: [
      "id", "doi", "title", "display_name",
      "publication_year", "publication_date", "cited_by_count",
      "abstract_inverted_index", "open_access",
      "primary_location", "best_oa_location",
      "topics", "keywords", "authorships",
      "referenced_works_count", "type"
    ].join(","),
  });
  if (mailto) params.set("mailto", mailto);
  return `${BASE_URL}/works?${params}`;
}

function buildBatchWorksUrl(ids, mailto) {
  // OpenAlex filter: openalex_id:W1|W2|W3 (max ~50 IDs per request)
  const filter = ids.map(id => {
    if (id.startsWith("W")) return `openalex_id:${id}`;
    if (id.startsWith("A")) return `openalex_id:${id}`;
    return `openalex_id:${id}`;
  }).join("|");

  const params = new URLSearchParams({
    filter: filter,
    "per-page": ids.length.toString(),
    select: [
      "id", "doi", "title", "display_name",
      "publication_year", "publication_date", "cited_by_count",
      "abstract_inverted_index", "open_access",
      "primary_location", "best_oa_location",
      "topics", "keywords", "authorships",
      "referenced_works_count", "type"
    ].join(","),
  });
  if (mailto) params.set("mailto", mailto);
  return `${BASE_URL}/works?${params}`;
}

function buildAuthorsUrl(query, limit, mailto) {
  const perPage = Math.min(limit, MAX_PER_PAGE);
  const params = new URLSearchParams();
  params.set("filter", `display_name.search:${query}`);
  params.set("per-page", perPage.toString());
  params.set("sort", "cited_by_count:desc");
  if (mailto) params.set("mailto", mailto);
  return `${BASE_URL}/authors?${params}`;
}

function buildJournalsUrl(query, limit, mailto) {
  const perPage = Math.min(limit, MAX_PER_PAGE);
  const params = new URLSearchParams({
    search: query,
    "per-page": perPage.toString(),
    sort: "cited_by_count:desc",
  });
  if (mailto) params.set("mailto", mailto);
  return `${BASE_URL}/sources?${params}`;
}

// ═════════════════════════════════════════════════════════════════════
//  MAPPING FUNCTIONS (OpenAlex API → SCITRACK schema)
// ═════════════════════════════════════════════════════════════════════

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
  const doiPrefix = doi.replace(/^https?:\/\/doi\.org\//i, "");
  return `https://doi.org/${doiPrefix}`;
}

function trimToLength(str, max) {
  if (!str) return null;
  return str.length > max ? str.substring(0, max) : str;
}

function resolvePdfUrl(work) {
  const loc = (work.best_oa_location?.pdf_url && work.best_oa_location)
    ? work.best_oa_location
    : work.primary_location;
  return loc?.pdf_url || null;
}

function extractJournal(work) {
  const src = work.primary_location?.source;
  if (!src?.display_name) return null;
  return {
    openAlexId: src.id || null,
    journalName: trimToLength(src.display_name, 500),
    issn: trimToLength(src.issn_l, 20),
    publisher: trimToLength(src.publisher || src.host_organization_name || null, 300),
  };
}

function extractAuthors(authorships) {
  if (!authorships || authorships.length === 0) return [];
  return authorships.slice(0, 20).map((a, i) => ({
    openAlexId:     a.author?.id || null,
    fullName:       a.author?.display_name || a.raw_author_name || "Unknown",
    rawAuthorName:  a.raw_author_name || null,
    affiliations:   a.raw_affiliation_strings || [],
    authorOrder:    i + 1,
    isCorresponding: false,
  }));
}

function extractKeywords(work) {
  const seen = new Set();
  const result = [];
  if (work.keywords) {
    for (const kw of work.keywords) {
      const text = kw.display_name || kw.keyword;
      if (text && !seen.has(text.toLowerCase())) {
        seen.add(text.toLowerCase());
        result.push({ openAlexId: kw.id || null, keyword: text, score: kw.score ?? null });
      }
    }
  }
  if (work.topics) {
    for (const t of work.topics.slice(0, 3)) {
      const text = t.display_name;
      if (text && !seen.has(text.toLowerCase())) {
        seen.add(text.toLowerCase());
        result.push({ openAlexId: t.id || null, keyword: text, score: t.score ?? null, source: "topic" });
      }
    }
  }
  return result.slice(0, 10);
}

function extractTopics(work) {
  if (!work.topics || work.topics.length === 0) return [];
  return work.topics.map(t => ({
    openAlexId: t.id || null,
    name:       t.display_name || null,
    score:      t.score ?? null,
    subfield:   t.subfield?.display_name || null,
    field:      t.field?.display_name || null,
    domain:     t.domain?.display_name || null,
  }));
}

function extractResearchField(work) {
  const topTopic = work.topics?.[0];
  if (!topTopic) return null;
  return {
    openAlexId: topTopic.field?.id || null,
    fieldName:  topTopic.field?.display_name || null,
    subfield:   topTopic.subfield?.display_name || null,
    domain:     topTopic.domain?.display_name || null,
  };
}

function mapPaper(work) {
  return {
    openAlexId:           work.id,
    type:                 work.type || null,
    title:                trimToLength(work.display_name || work.title, 1000),
    abstract:             rebuildAbstract(work.abstract_inverted_index),
    doi:                  normalizeDoi(work.doi),
    publicationDate:      work.publication_date || null,
    publicationYear:      work.publication_year ?? null,
    citationCount:        work.cited_by_count ?? 0,
    isOpenAccess:         work.open_access?.is_oa ?? false,
    pdfUrl:               resolvePdfUrl(work),
    landingPageUrl:       work.primary_location?.landing_page_url || null,
    referencedWorksCount: work.referenced_works_count ?? 0,
    journal:              extractJournal(work),
    authors:              extractAuthors(work.authorships),
    keywords:             extractKeywords(work),
    topics:               extractTopics(work),
    researchField:        extractResearchField(work),
  };
}

// ═════════════════════════════════════════════════════════════════════
//  SQL GENERATOR — produces executable SQL Server script
// ═════════════════════════════════════════════════════════════════════

function escapeSql(str) {
  if (!str) return "NULL";
  // SQL Server uses '' to escape single quotes
  return `N'${str.replace(/'/g, "''")}'`;
}

function escapeSqlNoN(str) {
  // For non-NVARCHAR fields (like dates, numbers handled separately)
  if (!str) return "NULL";
  return `'${str.replace(/'/g, "''")}'`;
}

function keywordNormalized(text) {
  return text.toLowerCase().trim();
}

function generateSql(papers, opts) {
  const lines = [];
  const ts = new Date().toISOString();

  lines.push("-- ═══════════════════════════════════════════════════════════════");
  lines.push(`-- SCITRACK — Generated by OpenAlex Scraper v2.0`);
  lines.push(`-- Generated at: ${ts}`);
  lines.push(`-- Papers: ${papers.length}`);
  lines.push(`-- Query: ${opts.query || "(batch by IDs)"}`);
  lines.push("--");
  lines.push("-- USAGE:");
  lines.push("--   1. Open this file in SSMS / Azure Data Studio");
  lines.push("--   2. Run against your SCITRACK database");
  lines.push("--   3. Duplicates are skipped (DOI for papers, ISSN for journals,");
  lines.push("--      NormalizedText for keywords, ExternalAuthorID for authors)");
  lines.push("-- ═══════════════════════════════════════════════════════════════");
  lines.push("");
  lines.push("SET NOCOUNT ON;  -- suppress row-count messages for performance");
  lines.push("GO");
  lines.push("");

  // Ensure API_SOURCE 'OpenAlex' exists (outside any paper transaction)
  lines.push("-- Ensure API_SOURCE exists");
  lines.push("IF NOT EXISTS (SELECT 1 FROM API_SOURCE WHERE SourceName = 'OpenAlex')");
  lines.push("BEGIN");
  lines.push("    INSERT INTO API_SOURCE (SourceID, SourceName, BaseURL, IsActive, RateLimitRPM)");
  lines.push(`    VALUES (NEWID(), N'OpenAlex', N'https://api.openalex.org', 1, 100);`);
  lines.push("END");
  lines.push("GO");
  lines.push("");
  lines.push("-- @SourceID is declared inside each paper transaction below");
  lines.push("");

  for (let i = 0; i < papers.length; i++) {
    const p = papers[i];
    const idx = i + 1;
    const oaId = p.openAlexId?.split("/").pop() || "UNKNOWN";

    lines.push("-- ───────────────────────────────────────────────────────────────");
    lines.push(`-- [${idx}/${papers.length}] ${(p.title || "UNTITLED").substring(0, 80)}`);
    lines.push(`-- OpenAlex: ${p.openAlexId || "N/A"}`);
    lines.push("-- ───────────────────────────────────────────────────────────────");
    lines.push("");
    lines.push("BEGIN TRANSACTION;  -- per-paper transaction for performance");
    lines.push("DECLARE @SourceID UNIQUEIDENTIFIER = (SELECT SourceID FROM API_SOURCE WHERE SourceName = 'OpenAlex');");

    // ── 1. JOURNAL (upsert by ISSN) ──
    if (p.journal && p.journal.issn) {
      lines.push("-- 1. Journal (upsert by ISSN)");
      lines.push("DECLARE @JournalID UNIQUEIDENTIFIER;");
      lines.push(`IF EXISTS (SELECT 1 FROM JOURNAL WHERE ISSN = ${escapeSql(p.journal.issn)})`);
      lines.push(`    SET @JournalID = (SELECT JournalID FROM JOURNAL WHERE ISSN = ${escapeSql(p.journal.issn)});`);
      lines.push("ELSE");
      lines.push("BEGIN");
      lines.push("    SET @JournalID = NEWID();");
      lines.push("    INSERT INTO JOURNAL (JournalID, SourceID, JournalName, ISSN, Publisher, IsActive)");
      lines.push(`    VALUES (@JournalID, @SourceID, ${escapeSql(p.journal.journalName)}, ${escapeSql(p.journal.issn)}, ${escapeSql(p.journal.publisher)}, 1);`);
      lines.push("END");
      lines.push("GO");
      lines.push("");
    } else {
      lines.push("-- (no journal)");
      lines.push("DECLARE @JournalID UNIQUEIDENTIFIER = NULL;");
      lines.push("GO");
      lines.push("");
    }

    // ── 2. RESEARCH_FIELD (upsert by FieldName) ──
    if (p.researchField && p.researchField.fieldName) {
      lines.push("-- 2. Research Field (upsert by FieldName)");
      lines.push("DECLARE @FieldID UNIQUEIDENTIFIER;");
      lines.push(`IF EXISTS (SELECT 1 FROM RESEARCH_FIELD WHERE FieldName = ${escapeSql(p.researchField.fieldName)})`);
      lines.push(`    SET @FieldID = (SELECT FieldID FROM RESEARCH_FIELD WHERE FieldName = ${escapeSql(p.researchField.fieldName)});`);
      lines.push("ELSE");
      lines.push("BEGIN");
      lines.push("    SET @FieldID = NEWID();");
      lines.push("    INSERT INTO RESEARCH_FIELD (FieldID, FieldName, IsTracked)");
      lines.push(`    VALUES (@FieldID, ${escapeSql(p.researchField.fieldName)}, 1);`);
      lines.push("END");
      lines.push("GO");
      lines.push("");
    } else {
      lines.push("-- (no research field)");
      lines.push("DECLARE @FieldID UNIQUEIDENTIFIER = NULL;");
      lines.push("GO");
      lines.push("");
    }

    // ── 3. RESEARCH_PAPER (upsert by DOI) ──
    lines.push("-- 3. Paper (skip if DOI exists)");
    lines.push("DECLARE @PaperID UNIQUEIDENTIFIER;");
    if (p.doi) {
      lines.push(`IF EXISTS (SELECT 1 FROM RESEARCH_PAPER WHERE DOI = ${escapeSql(p.doi)})`);
      lines.push("BEGIN");
      lines.push(`    SET @PaperID = (SELECT PaperID FROM RESEARCH_PAPER WHERE DOI = ${escapeSql(p.doi)});`);
      lines.push(`    PRINT '${oaId}: DOI already exists, skipping paper INSERT (using existing PaperID: ' + CAST(@PaperID AS NVARCHAR(36)) + ')';`);
      lines.push("    -- Still ensure journal/field links are updated");
      lines.push("    IF @JournalID IS NOT NULL");
      lines.push("        UPDATE RESEARCH_PAPER SET JournalID = @JournalID WHERE PaperID = @PaperID AND JournalID IS NULL;");
      lines.push("    IF @FieldID IS NOT NULL");
      lines.push("        UPDATE RESEARCH_PAPER SET FieldID = @FieldID WHERE PaperID = @PaperID AND FieldID IS NULL;");
      lines.push("END");
      lines.push("ELSE");
      lines.push("BEGIN");
    } else {
      lines.push("BEGIN");
    }
    lines.push("    SET @PaperID = NEWID();");
    lines.push("    INSERT INTO RESEARCH_PAPER (PaperID, SourceID, JournalID, FieldID, Title, Abstract, DOI, PubDate, PubYear, CitationCount, IsOpenAccess)");
    lines.push("    VALUES (");
    lines.push("        @PaperID,");
    lines.push("        @SourceID,");
    lines.push("        @JournalID,");
    lines.push("        @FieldID,");
    lines.push(`        ${escapeSql(p.title)},`);
    lines.push(`        ${escapeSql(p.abstract)},`);
    lines.push(`        ${p.doi ? escapeSql(p.doi) : "NULL"},`);
    lines.push(`        ${p.publicationDate ? escapeSqlNoN(p.publicationDate) : "NULL"},`);
    lines.push(`        ${p.publicationYear ?? "NULL"},`);
    lines.push(`        ${p.citationCount},`);
    lines.push(`        ${p.isOpenAccess ? 1 : 0}`);
    lines.push("    );");
    lines.push("END");
    lines.push("GO");
    lines.push("");

    // ── 4. AUTHORS + PAPER_AUTHOR ──
    if (p.authors.length > 0) {
      lines.push(`-- 4. Authors (${p.authors.length}) + paper_author junction`);
      lines.push("-- Upsert by (SourceID, ExternalAuthorID); skip if already linked");
      lines.push("DECLARE @AuthorID UNIQUEIDENTIFIER;  -- declared once, reused via SET");
      for (const author of p.authors) {
        const extId = author.openAlexId?.split("/").pop() || null;
        const extIdVal = extId ? escapeSql(extId) : "NULL";
        lines.push("");
        lines.push(`--   Author: ${(author.fullName || "Unknown").substring(0, 50)}`);
        if (extId) {
          lines.push(`IF EXISTS (SELECT 1 FROM AUTHOR WHERE SourceID = @SourceID AND ExternalAuthorID = ${extIdVal})`);
          lines.push(`    SET @AuthorID = (SELECT AuthorID FROM AUTHOR WHERE SourceID = @SourceID AND ExternalAuthorID = ${extIdVal});`);
          lines.push("ELSE");
          lines.push("BEGIN");
          lines.push("    SET @AuthorID = NEWID();");
          lines.push("    INSERT INTO AUTHOR (AuthorID, SourceID, ExternalAuthorID, FullName, Affiliation, HIndex, TotalCitations)");
          lines.push(`    VALUES (@AuthorID, @SourceID, ${extIdVal}, ${escapeSql(author.fullName)}, ${escapeSql(author.affiliations?.[0] || "Unknown")}, 0, 0);`);
          lines.push("END");
        } else {
          lines.push("SET @AuthorID = NEWID();");
          lines.push("INSERT INTO AUTHOR (AuthorID, SourceID, ExternalAuthorID, FullName, Affiliation, HIndex, TotalCitations)");
          lines.push(`VALUES (@AuthorID, @SourceID, NULL, ${escapeSql(author.fullName)}, ${escapeSql(author.affiliations?.[0] || "Unknown")}, 0, 0);`);
        }
        lines.push("");
        lines.push("-- Link paper-author (skip if already exists)");
        lines.push("IF NOT EXISTS (SELECT 1 FROM PAPER_AUTHOR WHERE PaperID = @PaperID AND AuthorID = @AuthorID)");
        lines.push("BEGIN");
        lines.push("    INSERT INTO PAPER_AUTHOR (PaperID, AuthorID, AuthorOrder, IsCorresponding)");
        lines.push(`    VALUES (@PaperID, @AuthorID, ${author.authorOrder}, ${author.isCorresponding ? 1 : 0});`);
        lines.push("END");
      }
    } else {
      lines.push("-- (no authors)");
    }
    lines.push("");

    // ── 5. KEYWORDS + PAPER_KEYWORD ──
    if (p.keywords.length > 0) {
      lines.push(`-- 5. Keywords (${p.keywords.length}) + paper_keyword junction`);
      lines.push("-- Upsert by NormalizedText; skip if already linked");
      lines.push("DECLARE @KeywordID UNIQUEIDENTIFIER;  -- declared once, reused via SET");
      for (const kw of p.keywords) {
        const norm = keywordNormalized(kw.keyword);
        const score = kw.score != null ? kw.score.toFixed(4) : "NULL";
        lines.push("");
        lines.push(`--   Keyword: ${kw.keyword}`);
        lines.push(`IF EXISTS (SELECT 1 FROM KEYWORD WHERE NormalizedText = ${escapeSql(norm)})`);
        lines.push("BEGIN");
        lines.push(`    SET @KeywordID = (SELECT KeywordID FROM KEYWORD WHERE NormalizedText = ${escapeSql(norm)});`);
        lines.push("    -- Increment paper count");
        lines.push("    UPDATE KEYWORD SET PaperCount = PaperCount + 1 WHERE KeywordID = @KeywordID;");
        lines.push("END");
        lines.push("ELSE");
        lines.push("BEGIN");
        lines.push("    SET @KeywordID = NEWID();");
        lines.push("    INSERT INTO KEYWORD (KeywordID, FieldID, KeywordText, NormalizedText, PaperCount)");
        lines.push(`    VALUES (@KeywordID, @FieldID, ${escapeSql(kw.keyword)}, ${escapeSql(norm)}, 1);`);
        lines.push("END");
        lines.push("");
        lines.push("-- Link paper-keyword (skip if already exists)");
        lines.push("IF NOT EXISTS (SELECT 1 FROM PAPER_KEYWORD WHERE PaperID = @PaperID AND KeywordID = @KeywordID)");
        lines.push("BEGIN");
        lines.push("    INSERT INTO PAPER_KEYWORD (PaperID, KeywordID, RelevanceScore)");
        lines.push(`    VALUES (@PaperID, @KeywordID, ${score});`);
        lines.push("END");
      }
    } else {
      lines.push("-- (no keywords)");
      lines.push("GO");
    }
    lines.push("COMMIT TRANSACTION;  -- commit this paper");
    lines.push("GO");
    lines.push("");
  }

  lines.push("-- ═══════════════════════════════════════════════════════════════");
  lines.push("-- All papers committed individually above");
  lines.push("-- ═══════════════════════════════════════════════════════════════");
  lines.push("");
  lines.push(`PRINT '✅ Done! Processed ${papers.length} papers from OpenAlex.';`);
  lines.push("GO");

  return postProcess(lines.join("\n"));
}

/**
 * Remove GO statements between BEGIN TRANSACTION and COMMIT TRANSACTION.
 * In SQL Server, GO destroys DECLARE variables, so sections within a paper
 * must all run in the same batch. We only keep COMMIT TRANSACTION; GO.
 */
function postProcess(sql) {
  const lines = sql.split("\n");
  const result = [];
  let inTransaction = false;

  for (let i = 0; i < lines.length; i++) {
    const trimmed = lines[i].trim();

    if (trimmed === "BEGIN TRANSACTION;  -- per-paper transaction for performance"
        || trimmed === "BEGIN TRANSACTION;") {
      inTransaction = true;
      result.push(lines[i]);
      continue;
    }

    if (trimmed === "COMMIT TRANSACTION;  -- commit this paper"
        || trimmed === "COMMIT TRANSACTION;") {
      inTransaction = false;
      result.push(lines[i]);
      result.push("GO");
      result.push("");
      continue;
    }

    // Skip GO inside transactions (variables must survive across sections)
    if (inTransaction && trimmed === "GO") {
      // Replace GO with blank line to keep formatting readable
      if (result.length > 0 && result[result.length - 1].trim() !== "") {
        result.push("");
      }
      continue;
    }

    result.push(lines[i]);
  }

  return result.join("\n");
}

// ═════════════════════════════════════════════════════════════════════
//  Main scraping functions
// ═════════════════════════════════════════════════════════════════════

async function scrapePapers(query, limit, mailto) {
  // limit = 0 means "fetch all available" (capped at OpenAlex's 10k limit)
  const fetchAll = (limit === 0);
  // For first page, use the actual limit if small; for fetchAll use max
  const firstPerPage = fetchAll ? MAX_PER_PAGE : Math.min(limit, MAX_PER_PAGE);

  console.log(`\n📄 Scraping papers for: "${query}"`);
  if (fetchAll) {
    console.log(`   Mode: FETCH ALL (up to ${OPENALEX_PAGINATION_CAP.toLocaleString()} results, ${MAX_PER_PAGE} per page)`);
  } else {
    console.log(`   Mode: limit ${limit} (${firstPerPage} per page)`);
  }

  // First request — discover total count
  const firstUrl = buildWorksUrl(query, firstPerPage, 1, mailto);
  console.log(`   [page 1] GET ${firstUrl.substring(0, 150)}...`);
  const firstData = await fetchWithRetry(firstUrl);
  if (!firstData) return [];

  const totalAvailable = firstData.meta?.count ?? 0;
  const allPapers = (firstData.results || []).map(mapPaper);
  console.log(`   ✓ page 1: ${allPapers.length} papers (total available: ${totalAvailable.toLocaleString()})`);

  if (allPapers.length === 0) return [];
  if (!fetchAll && allPapers.length >= limit) {
    console.log(`   ✓ Done (got enough in 1 page)`);
    return allPapers.slice(0, limit);
  }

  // Calculate how many more to fetch
  let targetCount;
  if (fetchAll) {
    targetCount = Math.min(totalAvailable, OPENALEX_PAGINATION_CAP);
  } else {
    targetCount = Math.min(limit, totalAvailable, OPENALEX_PAGINATION_CAP);
  }

  const totalPages = Math.ceil(targetCount / MAX_PER_PAGE);

  if (totalPages <= 1) {
    console.log(`   ✓ Done (single page)`);
    return allPapers.slice(0, limit > 0 ? limit : undefined);
  }

  console.log(`   📖 Will fetch ${totalPages - 1} more pages to reach ${targetCount.toLocaleString()} papers...`);

  // Fetch remaining pages (page 2 onwards, always use MAX_PER_PAGE for efficiency)
  for (let page = 2; page <= totalPages; page++) {
    const remaining = targetCount - allPapers.length;
    if (remaining <= 0) break;

    const url = buildWorksUrl(query, MAX_PER_PAGE, page, mailto);
    process.stdout.write(`   [page ${page}/${totalPages}] `);
    const data = await fetchWithRetry(url);

    if (!data) {
      console.warn(`⚠ failed, skipping...`);
      continue;
    }

    const pageResults = (data.results || []).map(mapPaper);
    allPapers.push(...pageResults);
    console.log(`${pageResults.length} papers (total: ${allPapers.length.toLocaleString()})`);

    // Polite delay between pages with random jitter (800ms–1500ms)
    if (page < totalPages) {
      const delay = randomDelay(800, 1500);
      process.stdout.write(`   ⏳ Waiting ${(delay / 1000).toFixed(1)}s (jitter)...\r`);
      await sleep(delay);
    }
  }

  const finalCount = fetchAll ? allPapers.length : Math.min(allPapers.length, limit);
  // Only warn about 10k cap when user actually wanted more than 10k
  const capped = (fetchAll && totalAvailable > OPENALEX_PAGINATION_CAP)
    ? totalAvailable - OPENALEX_PAGINATION_CAP : 0;
  console.log(`\n   ✅ Fetched ${finalCount.toLocaleString()} papers total`);
  if (capped > 0) {
    console.log(`   ⚠ ${capped.toLocaleString()} papers beyond OpenAlex's 10k pagination limit were skipped.`);
    console.log(`   💡 For >10k results, use OpenAlex Snapshot: https://openalex.org/download`);
  }

  return allPapers.slice(0, limit > 0 ? limit : undefined);
}

async function scrapeBatchPapers(ids, mailto) {
  console.log(`\n📄 Batch fetching ${ids.length} papers by OpenAlex ID...`);

  const allPapers = [];
  // Chunk IDs to avoid URL length limits (~50 IDs per request)
  for (let i = 0; i < ids.length; i += BATCH_CHUNK_SIZE) {
    const chunk = ids.slice(i, i + BATCH_CHUNK_SIZE);
    const url = buildBatchWorksUrl(chunk, mailto);
    console.log(`   [${i + 1}-${Math.min(i + BATCH_CHUNK_SIZE, ids.length)}/${ids.length}] GET ${url.substring(0, 150)}...`);
    const data = await fetchWithRetry(url);
    if (!data) {
      console.warn(`   ⚠ Batch chunk ${i + 1}-${i + chunk.length} failed, continuing...`);
      continue;
    }
    const results = data.results || [];
    console.log(`   ✓ ${results.length} papers returned`);
    allPapers.push(...results.map(mapPaper));
    // Jittered delay between batch chunks
    if (i + BATCH_CHUNK_SIZE < ids.length) {
      const delay = randomDelay(500, 1200);
      await sleep(delay);
    }
  }

  // Check for missing IDs
  const returnedIds = new Set(allPapers.map(p => p.openAlexId?.split("/").pop()?.toUpperCase()));
  const missing = ids.filter(id => !returnedIds.has(id.toUpperCase()));
  if (missing.length > 0) {
    console.warn(`   ⚠ ${missing.length} IDs not found: ${missing.join(", ")}`);
  }

  return allPapers;
}

// ── Output ───────────────────────────────────────────────────────────

function printStats(papers) {
  if (!papers || papers.length === 0) return;

  const totalCitations = papers.reduce((sum, p) => sum + (p.citationCount || 0), 0);
  const avgCitations = (totalCitations / papers.length).toFixed(1);
  const openAccessCount = papers.filter(p => p.isOpenAccess).length;
  const withDoi = papers.filter(p => p.doi).length;
  const withAbstract = papers.filter(p => p.abstract).length;
  const top = papers.slice().sort((a, b) => b.citationCount - a.citationCount)[0];

  const yearMap = {};
  papers.forEach(p => {
    const y = p.publicationYear || "unknown";
    yearMap[y] = (yearMap[y] || 0) + 1;
  });
  const yearSorted = Object.entries(yearMap).sort((a, b) => b[1] - a[1]).slice(0, 5);

  const journalMap = {};
  papers.forEach(p => {
    if (p.journal?.journalName) {
      journalMap[p.journal.journalName] = (journalMap[p.journal.journalName] || 0) + 1;
    }
  });
  const journalSorted = Object.entries(journalMap).sort((a, b) => b[1] - a[1]).slice(0, 5);

  const ln = (label, value) => {
    const s = `  ${label}${value}`;
    return `║${s.padEnd(52)}║`;
  };

  console.log("\n╔══════════════════════════════════════════════════════╗");
  console.log("║  📊  SUMMARY                                       ║");
  console.log("╠══════════════════════════════════════════════════════╣");
  console.log(ln("Papers:          ", papers.length.toLocaleString()));
  console.log(ln("Total citations: ", totalCitations.toLocaleString()));
  console.log(ln("Avg citations:   ", avgCitations));
  console.log(ln("Open access:     ", `${openAccessCount} (${((openAccessCount/papers.length)*100).toFixed(1)}%)`));
  console.log(ln("With DOI:        ", withDoi.toString()));
  console.log(ln("With abstract:   ", withAbstract.toString()));
  console.log("╠══════════════════════════════════════════════════════╣");
  console.log(ln("🏆 Top cited:     ", `${top.citationCount.toLocaleString()} cites`));
  console.log(ln("                  ", `"${(top.title || "N/A").substring(0, 35)}"`));
  console.log("╠══════════════════════════════════════════════════════╣");
  console.log("║  📅 Top years:                                     ║");
  yearSorted.forEach(([year, count]) => console.log(ln(`  ${year}: `, count.toString())));
  if (journalSorted.length > 0) {
    console.log("╠══════════════════════════════════════════════════════╣");
    console.log("║  📰 Top journals:                                  ║");
    journalSorted.forEach(([name, count]) => {
      const short = name.length > 28 ? name.substring(0, 25) + "..." : name;
      console.log(ln(`  ${short}: `, count.toString()));
    });
  }
  console.log("╚══════════════════════════════════════════════════════╝");
}

function saveOutput(filename, data) {
  mkdirSync(OUTPUT_DIR, { recursive: true });
  const path = `${OUTPUT_DIR}/${filename}`;
  writeFileSync(path, data, "utf-8");
  console.log(`\n💾 Saved: ${path} (${data.length.toLocaleString()} bytes)`);
}

async function scrapeAuthors(query, limit, mailto) {
  console.log(`\n👤 Scraping authors for: "${query}" (limit: ${limit})`);
  const url = buildAuthorsUrl(query, limit, mailto);
  console.log(`   GET ${url}`);
  const data = await fetchWithRetry(url);
  if (!data) return [];
  const count = data.meta?.count ?? 0;
  console.log(`   ✓ ${data.results?.length ?? 0} authors returned (total available: ${count})`);
  return (data.results || []);
}

async function scrapeJournals(query, limit, mailto) {
  console.log(`\n📰 Scraping journals for: "${query}" (limit: ${limit})`);
  const url = buildJournalsUrl(query, limit, mailto);
  console.log(`   GET ${url}`);
  const data = await fetchWithRetry(url);
  if (!data) return [];
  const count = data.meta?.count ?? 0;
  console.log(`   ✓ ${data.results?.length ?? 0} sources returned (total available: ${count})`);
  const onlyJournals = (data.results || []).filter(r => r.type === "journal");
  return onlyJournals;
}

// ═════════════════════════════════════════════════════════════════════
//  INTERACTIVE MODE — hỏi từng câu, không cần nhớ CLI flags
// ═════════════════════════════════════════════════════════════════════

function ask(rl, question) {
  return new Promise(resolve => {
    rl.question(question, answer => resolve(answer.trim()));
  });
}

async function interactiveMode() {
  const rl = createInterface({ input: process.stdin, output: process.stdout });

  console.log("\n╔══════════════════════════════════════════════════════╗");
  console.log("║  OpenAlex Scraper — Interactive Mode                ║");
  console.log("╚══════════════════════════════════════════════════════╝\n");

  // ── Email (REQUIRED) ──
  let mailto = "";
  while (!mailto || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(mailto)) {
    mailto = await ask(rl, "📧 Your email (REQUIRED for polite pool — 100k req/day): ");
    if (!mailto || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(mailto)) {
      console.log("   ❌ Email is required to avoid rate limiting. Please enter a valid email.");
    }
  }

  // ── Mode: search hay batch ──
  console.log("\n📋 Choose mode:");
  console.log("   [1] Search by keyword");
  console.log("   [2] Batch by OpenAlex IDs / URLs");
  const mode = await ask(rl, "   Enter 1 or 2 [1]: ");
  const isBatch = (mode === "2");

  let query, ids, opts;

  if (isBatch) {
    console.log("\n📎 Paste OpenAlex URLs or IDs (one per line, empty line to finish):");
    const lines = [];
    while (true) {
      const line = await ask(rl, "   > ");
      if (!line) break;
      lines.push(line);
    }
    ids = parseOpenAlexIds(lines.join("\n"));
    if (ids.length === 0) {
      console.error("❌ No valid OpenAlex IDs found.");
      rl.close();
      process.exit(1);
    }
    console.log(`   ✓ Parsed ${ids.length} unique IDs`);
    query = `batch ${ids.length} IDs`;
    opts = { entity: "batch", ids: lines.join(","), limit: ids.length, format: "sql", mailto };
  } else {
    query = await ask(rl, "🔑 Keyword (e.g. machine unlearning): ");
    if (!query) {
      console.error("❌ Keyword is required.");
      rl.close();
      process.exit(1);
    }

    const limitStr = await ask(rl, `📊 How many papers? [0-${OPENALEX_PAGINATION_CAP}, 0=ALL, default=${DEFAULT_LIMIT}]: `);
    const limit = limitStr === "" ? DEFAULT_LIMIT
                : limitStr === "0" ? 0
                : parseInt(limitStr, 10);

    if (isNaN(limit) || limit < 0 || limit > OPENALEX_PAGINATION_CAP) {
      console.error(`❌ Limit must be 0-${OPENALEX_PAGINATION_CAP}`);
      rl.close();
      process.exit(1);
    }
    opts = { entity: "papers", query, limit, format: "sql", mailto };
  }

  rl.close();

  // Print summary & confirm
  console.log("\n╔══════════════════════════════════════════════════════╗");
  console.log("║  Ready to scrape!                                   ║");
  console.log("╠══════════════════════════════════════════════════════╣");
  console.log(`║  Mode:    ${isBatch ? "Batch by IDs" : "Search by keyword"}`);
  console.log(`║  Query:   ${(query || "").substring(0, 42)}`);
  if (!isBatch) console.log(`║  Limit:   ${opts.limit === 0 ? "ALL (up to 10k)" : opts.limit}`);
  console.log(`║  Email:   ${mailto} (polite pool)`);
  console.log(`║  Output:  SQL file in ./output/`);
  console.log("╚══════════════════════════════════════════════════════╝");

  return opts;
}

// ── Entry point ─────────────────────────────────────────────────────

const HAS_CLI_ARGS = process.argv.length > 2;

if (!HAS_CLI_ARGS) {
  // No arguments → interactive mode
  const opts = await interactiveMode();
  await runWithOpts(opts);
} else {
  // CLI mode
  await runWithOpts(parseArgs());
}

async function runWithOpts(opts) {
  const t0 = Date.now();
  const ts = new Date().toISOString().replace(/[:.]/g, "-");

  console.log("╔══════════════════════════════════════════════════════╗");
  console.log("║  OpenAlex Scraper v2.0 — SCITRACK Data Pipeline     ║");
  console.log("╚══════════════════════════════════════════════════════╝");
  console.log(`   Entity: ${opts.entity} | Format: ${opts.format} | Limit: ${opts.limit}`);

  let papers;

  if (opts.entity === "batch") {
    const ids = opts.idsFile
      ? loadIdsFromFile(opts.idsFile)
      : parseOpenAlexIds(opts.ids);
    if (ids.length === 0) {
      console.error("Error: no valid OpenAlex IDs found");
      process.exit(1);
    }
    console.log(`   Parsed ${ids.length} unique OpenAlex IDs`);

    papers = await scrapeBatchPapers(ids, opts.mailto);
    if (papers.length === 0) {
      console.error("Error: no papers retrieved");
      process.exit(1);
    }

    if (opts.format === "sql") {
      const sql = generateSql(papers, { ...opts, query: `batch ${ids.length} IDs` });
      saveOutput(`batch_papers_${ts}.sql`, sql);
    } else {
      saveOutput(`batch_papers_${ts}.json`, JSON.stringify({
        query: `batch ${ids.length} IDs`,
        total: papers.length,
        results: papers,
      }, null, 2));
    }
  } else {
    papers = await scrapePapers(opts.query, opts.limit === 0 ? 0 : opts.limit, opts.mailto);
    if (papers.length === 0) {
      console.error("Error: no papers retrieved");
      process.exit(1);
    }
    if (opts.format === "sql") {
      const sql = generateSql(papers, opts);
      saveOutput(`papers_${ts}.sql`, sql);
    } else {
      saveOutput(`papers_${ts}.json`, JSON.stringify({
        query: opts.query, total: papers.length, results: papers,
      }, null, 2));
    }
  }

  printStats(papers);
  const elapsed = ((Date.now() - t0) / 1000).toFixed(1);
  console.log(`\n✅ Done in ${elapsed}s`);
}
