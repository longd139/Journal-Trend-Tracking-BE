#!/usr/bin/env node

/**
 * TEST IMPORTER — Lấy 10-20 papers thật từ OpenAlex API và sinh SQL test.
 *
 * Dùng để kiểm tra pipeline import trước khi tải toàn bộ snapshot (100GB+).
 * Script này gọi API (nhanh, free), chạy qua đúng logic mapPaper() và generateBatchFile()
 * của importer.js, rồi sinh ra 1 file SQL nhỏ (~200KB) để test trên SQL Server.
 *
 * Usage:
 *   node test-importer.js
 *   node test-importer.js --count 20 --keyword "covid"
 *   node test-importer.js --count 5 --year 2024
 */

import { writeFileSync, mkdirSync } from "fs";

// ═══════════════════════════════════════════════════════════════
//  Reuse mapping logic từ importer.js (copy những hàm cần thiết)
// ═══════════════════════════════════════════════════════════════

const TITLE_MAX_LENGTH = 1000;
const MAX_AUTHORS_PER_PAPER = 5;
const MAX_KEYWORDS_PER_PAPER = 8;

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
//  SQL Generator (giống hệt importer.js)
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

function generateTestSql(papers) {
  const ts = new Date().toISOString();
  const lines = [];

  lines.push("-- ═══════════════════════════════════════════════════════════════");
  lines.push("-- SCITRACK — TEST IMPORT FILE");
  lines.push(`-- Generated: ${ts}`);
  lines.push(`-- Papers: ${papers.length} (TEST — small sample from OpenAlex API)`);
  lines.push("--");
  lines.push("-- 🧪 This is a TEST file. Run it to verify the SQL import pipeline");
  lines.push("--    works correctly before downloading the full 100GB snapshot.");
  lines.push("-- ═══════════════════════════════════════════════════════════════");
  lines.push("");
  lines.push("SET NOCOUNT ON;");
  lines.push("GO");
  lines.push("");
  lines.push("-- Ensure API_SOURCE 'OpenAlex' exists");
  lines.push("IF NOT EXISTS (SELECT 1 FROM API_SOURCE WHERE SourceName = 'OpenAlex')");
  lines.push("BEGIN");
  lines.push("    INSERT INTO API_SOURCE (SourceID, SourceName, BaseURL, IsActive, RateLimitRPM)");
  lines.push("    VALUES (NEWID(), N'OpenAlex', N'https://api.openalex.org', 1, 100);");
  lines.push("END");
  lines.push("GO");
  lines.push("");

  // ── Per-paper transaction (an toàn nhất cho test) ──
  papers.forEach((paper, idx) => {
    const num = idx + 1;
    const oaId = paper.openAlexId?.split("/").pop() || `PAPER_${num}`;

    lines.push("-- ───────────────────────────────────────────────────────────────");
    lines.push(`-- TEST PAPER [${num}/${papers.length}] ${(paper.title || "").substring(0, 70)}`);
    lines.push(`-- OpenAlex: ${paper.openAlexId || "N/A"}`);
    lines.push(`-- Year: ${paper.publicationYear || "?"} | Citations: ${paper.citationCount}`);
    lines.push(`-- DOI: ${paper.doi || "none"} | Type: ${paper.type || "unknown"}`);
    lines.push("-- ───────────────────────────────────────────────────────────────");
    lines.push("");
    lines.push("BEGIN TRY");
    lines.push("  BEGIN TRANSACTION;");
    lines.push("  DECLARE @SourceID UNIQUEIDENTIFIER = (SELECT SourceID FROM API_SOURCE WHERE SourceName = 'OpenAlex');");
    lines.push("  DECLARE @PaperID UNIQUEIDENTIFIER = NEWID();");
    lines.push("  DECLARE @JournalID UNIQUEIDENTIFIER = NULL;");
    lines.push("  DECLARE @FieldID UNIQUEIDENTIFIER = NULL;");
    lines.push("");

    // Journal
    if (paper.journal) {
      if (paper.journal.issn) {
        lines.push(`  IF NOT EXISTS (SELECT 1 FROM JOURNAL WHERE ISSN = ${escapeSql(paper.journal.issn)})`);
        lines.push("  BEGIN");
        lines.push("      SET @JournalID = NEWID();");
        lines.push("      INSERT INTO JOURNAL (JournalID, SourceID, JournalName, ISSN, Publisher, IsActive)");
        lines.push(`      VALUES (@JournalID, @SourceID, ${escapeSql(paper.journal.displayName)}, ${escapeSql(paper.journal.issn)}, ${escapeSql(paper.journal.publisher)}, 1);`);
        lines.push("  END");
        lines.push("  ELSE");
        lines.push(`      SET @JournalID = (SELECT TOP 1 JournalID FROM JOURNAL WHERE ISSN = ${escapeSql(paper.journal.issn)});`);
      } else {
        lines.push(`  -- No ISSN for journal: ${(paper.journal.displayName || "?").substring(0, 50)}`);
        lines.push(`  IF NOT EXISTS (SELECT 1 FROM JOURNAL WHERE JournalName = ${escapeSql(paper.journal.displayName)})`);
        lines.push("  BEGIN");
        lines.push("      SET @JournalID = NEWID();");
        lines.push("      INSERT INTO JOURNAL (JournalID, SourceID, JournalName, ISSN, Publisher, IsActive)");
        lines.push(`      VALUES (@JournalID, @SourceID, ${escapeSql(paper.journal.displayName)}, NULL, ${escapeSql(paper.journal.publisher)}, 1);`);
        lines.push("  END");
        lines.push("  ELSE");
        lines.push(`      SET @JournalID = (SELECT TOP 1 JournalID FROM JOURNAL WHERE JournalName = ${escapeSql(paper.journal.displayName)});`);
      }
      lines.push("");
    }

    // Research Field
    if (paper.researchField?.fieldName) {
      lines.push(`  IF NOT EXISTS (SELECT 1 FROM RESEARCH_FIELD WHERE FieldName = ${escapeSql(paper.researchField.fieldName)})`);
      lines.push("  BEGIN");
      lines.push("      SET @FieldID = NEWID();");
      lines.push("      INSERT INTO RESEARCH_FIELD (FieldID, FieldName, IsTracked)");
      lines.push(`      VALUES (@FieldID, ${escapeSql(paper.researchField.fieldName)}, 1);`);
      lines.push("  END");
      lines.push("  ELSE");
      lines.push(`      SET @FieldID = (SELECT TOP 1 FieldID FROM RESEARCH_FIELD WHERE FieldName = ${escapeSql(paper.researchField.fieldName)});`);
      lines.push("");
    }

    // Paper
    lines.push("  -- Check duplicate by DOI");
    if (paper.doi) {
      lines.push(`  IF EXISTS (SELECT 1 FROM RESEARCH_PAPER WHERE DOI = ${escapeSql(paper.doi)})`);
      lines.push(`      THROW 50000, 'SKIP_DOI: ${oaId}', 1;`);
    }
    lines.push("");
    lines.push("  INSERT INTO RESEARCH_PAPER (PaperID, SourceID, JournalID, FieldID, Title, Abstract, DOI, PubDate, PubYear, CitationCount, IsOpenAccess, PdfUrl)");
    lines.push("  VALUES (");
    lines.push("      @PaperID, @SourceID, @JournalID, @FieldID,");
    lines.push(`      ${escapeSql(paper.title)},`);
    lines.push(`      ${escapeSql(paper.abstract)},`);
    lines.push(`      ${paper.doi ? escapeSql(paper.doi) : "NULL"},`);
    lines.push(`      ${paper.publicationDate ? escapeSqlNoN(paper.publicationDate) : "NULL"},`);
    lines.push(`      ${paper.publicationYear ?? "NULL"},`);
    lines.push(`      ${paper.citationCount},`);
    lines.push(`      ${paper.isOpenAccess ? 1 : 0},`);
    lines.push(`      ${escapeSql(paper.pdfUrl)}`);
    lines.push("  );");
    lines.push("");

    // Authors
    if (paper.authors.length > 0) {
      lines.push(`  -- Authors (${paper.authors.length})`);
      for (const author of paper.authors) {
        const extId = author.openAlexId?.split("/").pop() || null;
        const extIdVal = extId ? escapeSql(extId) : "NULL";
        const varName = `@AID_${author.authorOrder}`;

        if (extId) {
          lines.push(`  IF NOT EXISTS (SELECT 1 FROM AUTHOR WHERE SourceID = @SourceID AND ExternalAuthorID = ${extIdVal})`);
          lines.push("  BEGIN");
          lines.push(`      DECLARE ${varName} UNIQUEIDENTIFIER = NEWID();`);
          lines.push("      INSERT INTO AUTHOR (AuthorID, SourceID, ExternalAuthorID, FullName, Affiliation, HIndex, TotalCitations)");
          lines.push(`      VALUES (${varName}, @SourceID, ${extIdVal}, ${escapeSql(author.fullName)}, ${escapeSql(author.affiliations?.[0] || "Unknown")}, 0, 0);`);
          lines.push("  END");
          lines.push("  ELSE");
          lines.push(`      DECLARE ${varName} UNIQUEIDENTIFIER = (SELECT AuthorID FROM AUTHOR WHERE SourceID = @SourceID AND ExternalAuthorID = ${extIdVal});`);
        } else {
          lines.push(`  DECLARE ${varName} UNIQUEIDENTIFIER = NEWID();`);
          lines.push("  INSERT INTO AUTHOR (AuthorID, SourceID, ExternalAuthorID, FullName, Affiliation, HIndex, TotalCitations)");
          lines.push(`  VALUES (${varName}, @SourceID, NULL, ${escapeSql(author.fullName)}, ${escapeSql(author.affiliations?.[0] || "Unknown")}, 0, 0);`);
        }
        lines.push("");
        lines.push(`  IF NOT EXISTS (SELECT 1 FROM PAPER_AUTHOR WHERE PaperID = @PaperID AND AuthorID = ${varName})`);
        lines.push(`      INSERT INTO PAPER_AUTHOR (PaperID, AuthorID, AuthorOrder, IsCorresponding) VALUES (@PaperID, ${varName}, ${author.authorOrder}, 0);`);
        lines.push("");
      }
    }

    // Keywords
    if (paper.keywords.length > 0) {
      lines.push(`  -- Keywords (${paper.keywords.length})`);
      for (let i = 0; i < paper.keywords.length; i++) {
        const kw = paper.keywords[i];
        const norm = keywordNormalized(kw.keyword);
        const score = kw.score != null ? kw.score.toFixed(4) : "NULL";
        lines.push(`  DECLARE @KWID_${i} UNIQUEIDENTIFIER;`);
        lines.push(`  IF EXISTS (SELECT 1 FROM KEYWORD WHERE NormalizedText = ${escapeSql(norm)})`);
        lines.push("  BEGIN");
        lines.push(`      SET @KWID_${i} = (SELECT TOP 1 KeywordID FROM KEYWORD WHERE NormalizedText = ${escapeSql(norm)});`);
        lines.push(`      UPDATE KEYWORD SET PaperCount = ISNULL(PaperCount, 0) + 1 WHERE KeywordID = @KWID_${i};`);
        lines.push("  END");
        lines.push("  ELSE");
        lines.push("  BEGIN");
        lines.push(`      SET @KWID_${i} = NEWID();`);
        lines.push(`      INSERT INTO KEYWORD (KeywordID, FieldID, KeywordText, NormalizedText, PaperCount) VALUES (@KWID_${i}, @FieldID, ${escapeSql(kw.keyword)}, ${escapeSql(norm)}, 1);`);
        lines.push("  END");
        lines.push(`  IF NOT EXISTS (SELECT 1 FROM PAPER_KEYWORD WHERE PaperID = @PaperID AND KeywordID = @KWID_${i})`);
        lines.push(`      INSERT INTO PAPER_KEYWORD (PaperID, KeywordID, RelevanceScore) VALUES (@PaperID, @KWID_${i}, ${score});`);
        lines.push("");
      }
    }

    lines.push("  COMMIT TRANSACTION;");
    lines.push(`  PRINT '✅ [${num}/${papers.length}] OK: ${oaId} — ${(paper.title || "").substring(0, 50)}';`);
    lines.push("END TRY");
    lines.push("BEGIN CATCH");
    lines.push("  IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;");
    lines.push("  DECLARE @ErrMsg NVARCHAR(MAX) = ERROR_MESSAGE();");
    lines.push("  IF @ErrMsg LIKE '%SKIP_DOI%' OR @ErrMsg LIKE '%SKIP_TITLE%'");
    lines.push(`      PRINT '⏭ [${num}/${papers.length}] SKIP: ${oaId} — duplicate (already in DB)';`);
    lines.push("  ELSE");
    lines.push("  BEGIN");
    lines.push(`      PRINT '❌ [${num}/${papers.length}] ERROR: ${oaId} — ' + @ErrMsg + ' (Line: ' + CAST(ERROR_LINE() AS NVARCHAR) + ')';`);
    lines.push("      THROW;");
    lines.push("  END");
    lines.push("END CATCH");
    lines.push("GO");
    lines.push("");
  });

  lines.push("-- ═══════════════════════════════════════════════════════════════");
  lines.push(`-- ✅ TEST COMPLETE: ${papers.length} papers processed.`);
  lines.push("--    If all printed 'OK' or 'SKIP', the pipeline works correctly!");
  lines.push("--    You are now ready to run the full importer with real snapshot data.");
  lines.push("-- ═══════════════════════════════════════════════════════════════");
  lines.push("GO");

  return lines.join("\n");
}

// ═══════════════════════════════════════════════════════════════
//  OpenAlex API — fetch a few papers
// ═══════════════════════════════════════════════════════════════

async function fetchTestPapers(keyword, count, year) {
  const BASE_URL = "https://api.openalex.org";
  const select = [
    "id", "doi", "title", "display_name",
    "publication_year", "publication_date", "cited_by_count",
    "abstract_inverted_index", "open_access",
    "primary_location", "best_oa_location",
    "topics", "keywords", "authorships",
    "referenced_works_count", "type"
  ].join(",");

  let filter = "";
  if (year) {
    filter = `from_publication_date:${year}-01-01,to_publication_date:${year}-12-31`;
  }

  const params = new URLSearchParams({
    search: keyword,
    sort: "cited_by_count:desc",
    "per-page": Math.min(count, 200).toString(),
    select,
  });
  if (filter) params.set("filter", filter);

  const url = `${BASE_URL}/works?${params}`;
  console.log(`\n🌐 Fetching ${count} papers from OpenAlex API...`);
  console.log(`   Keyword: "${keyword}"${year ? ` | Year: ${year}` : ""}`);
  console.log(`   URL: ${url.substring(0, 120)}...`);

  const res = await fetch(url);
  if (!res.ok) {
    const body = await res.text();
    console.error(`❌ API error ${res.status}: ${body.substring(0, 300)}`);
    process.exit(1);
  }

  const data = await res.json();
  const papers = (data.results || []).map(mapPaper);

  console.log(`   ✓ Got ${papers.length} papers (total available: ${(data.meta?.count || 0).toLocaleString()})`);

  // Print summary of fetched papers
  console.log("\n📋 Papers fetched:");
  papers.forEach((p, i) => {
    const title = (p.title || "Untitled").substring(0, 70);
    console.log(`   ${i + 1}. [${p.publicationYear || "?"}] ${title}`);
    console.log(`      DOI: ${p.doi || "none"} | Citations: ${p.citationCount} | Authors: ${p.authors.length}`);
  });

  return papers;
}

// ═══════════════════════════════════════════════════════════════
//  Main
// ═══════════════════════════════════════════════════════════════

function parseArgs() {
  const args = process.argv.slice(2);
  const opts = { count: 10, keyword: "machine learning", year: null };
  for (let i = 0; i < args.length; i++) {
    switch (args[i]) {
      case "--count":   case "-c": opts.count = parseInt(args[++i], 10); break;
      case "--keyword": case "-k": opts.keyword = args[++i]; break;
      case "--year":    case "-y": opts.year = parseInt(args[++i], 10); break;
    }
  }
  return opts;
}

const opts = parseArgs();

console.log("╔══════════════════════════════════════════════════════╗");
console.log("║  🧪 TEST IMPORTER — Verify SQL pipeline             ║");
console.log("║  Fetches a few papers from API → generates SQL      ║");
console.log("╚══════════════════════════════════════════════════════╝");

const papers = await fetchTestPapers(opts.keyword, opts.count, opts.year);

if (papers.length === 0) {
  console.error("❌ No papers returned. Try a different keyword.");
  process.exit(1);
}

// Generate SQL
const sql = generateTestSql(papers);
const outDir = "./output";
mkdirSync(outDir, { recursive: true });
const filename = `TEST_papers_${papers.length}_${opts.keyword.replace(/\s+/g, "_")}.sql`;
const filepath = `${outDir}/${filename}`;
writeFileSync(filepath, sql, "utf-8");

const sizeKB = (Buffer.byteLength(sql, "utf-8") / 1024).toFixed(1);
console.log(`\n💾 Saved: ${filepath} (${sizeKB} KB)`);
console.log("");
console.log("╔══════════════════════════════════════════════════════╗");
console.log("║  ✅ NEXT STEPS                                      ║");
console.log("╠══════════════════════════════════════════════════════╣");
console.log("║  1. Open the SQL file in SSMS / Azure Data Studio   ║");
console.log(`║     → ${filepath}`);
console.log("║  2. Connect to your SCITRACK database               ║");
console.log("║  3. Press F5 to execute                             ║");
console.log("║  4. Check output messages:                          ║");
console.log("║     ✅ OK = paper inserted                          ║");
console.log("║     ⏭ SKIP = duplicate (safe to re-run)             ║");
console.log("║     ❌ ERROR = schema mismatch → fix & re-test       ║");
console.log("╠══════════════════════════════════════════════════════╣");
console.log("║  If all papers show OK or SKIP:                     ║");
console.log("║  → Your DB schema is correct!                       ║");
console.log("║  → Run the full importer:                           ║");
console.log("║    node importer.js -i <snapshot-dir> --year 2024    ║");
console.log("╚══════════════════════════════════════════════════════╝");
