#!/usr/bin/env node

/**
 * Fix old-format SQL files: convert one giant transaction → per-paper transactions.
 *
 * Usage:
 *   node fix-sql.js <input.sql> [output.sql]      — fix one file
 *   node fix-sql.js --all                           — fix ALL .sql files in ./output/
 */

import { readFileSync, writeFileSync, readdirSync, statSync, renameSync } from "fs";
import { join, basename, dirname } from "path";

function fixSql(content) {
  const lines = content.split("\n");

  // Step 1: Remove old global BEGIN/COMMIT TRANSACTION
  const filtered = lines.filter(line => {
    const trimmed = line.trim();
    return trimmed !== "BEGIN TRANSACTION;"
        && trimmed !== "COMMIT TRANSACTION;"
        && !trimmed.startsWith("-- Cleanup:");
  });

  // Step 2: Add SET NOCOUNT ON after the header comment block
  // Find the line after "USAGE:" section (last header comment before SQL starts)
  const result = [];
  let headerDone = false;
  let inPaper = false;

  for (let i = 0; i < filtered.length; i++) {
    const line = filtered[i];
    const trimmed = line.trim();

    // Insert SET NOCOUNT ON after the header comments, before DECLARE @SourceID
    if (!headerDone && trimmed.startsWith("DECLARE @SourceID")) {
      result.push("SET NOCOUNT ON;  -- suppress row-count messages for performance");
      result.push("GO");
      result.push("");
      headerDone = true;
    }

    // Detect paper start: "-- [N/TOTAL]"
    if (/^--\s+\[\d+\/\d+\]/.test(trimmed)) {
      // Add BEGIN TRANSACTION before the paper header section
      // (backtrack: insert before the "-- ────" separator line that precedes [N])
      // Actually the pattern is: separator line, then [N/TOTAL] line, then separator, then blank
      // Let's just add BEGIN TRANSACTION after the second separator line
      inPaper = true;
    }

    // First blank line after paper header = place to put BEGIN TRANSACTION
    if (inPaper && trimmed === "" && result.length > 0 && result[result.length - 1].trim() === "-- ───────────────────────────────────────────────────────────────") {
      result.push("");
      result.push("BEGIN TRANSACTION;");
      inPaper = false; // done with this paper's header
    }

    result.push(line);

    // Detect paper end: "-- (no keywords)" followed by GO, then blank line
    // OR the last keyword's GO followed by blank line before next paper or end
    if (trimmed === "GO" && i > 0) {
      const prev = filtered[i - 1].trim();
      if (prev === "-- (no keywords)" || (prev === "END" && filtered[i - 2]?.trim()?.startsWith("-- Link paper-keyword"))) {
        // This is the end of a paper
      }
    }
  }

  // Simpler approach: rebuild with regex
  return fixSqlRegex(content);
}

function fixSqlRegex(content) {
  // 1. Remove global BEGIN TRANSACTION and COMMIT TRANSACTION (old format)
  let fixed = content
    .replace(/^BEGIN TRANSACTION;\s*GO\s*/gm, "")
    .replace(/^COMMIT TRANSACTION;\s*GO\s*/gm, "")
    .replace(/^-- Cleanup:.*\n/gm, "");

  // 2. Add SET NOCOUNT ON before DECLARE @SourceID
  fixed = fixed.replace(
    /(DECLARE @SourceID)/,
    "SET NOCOUNT ON;\nGO\n\n$1"
  );

  // 3. Wrap each paper in its own transaction.
  //    Paper header pattern: "-- <dashes>\n-- [N/TOTAL] Title..."
  //    Add BEGIN TRANSACTION before the paper header.
  //    Add COMMIT TRANSACTION after the last GO of each paper section.

  // Step 3a: Insert BEGIN TRANSACTION before each paper header
  //    BUT only if there isn't already one there (idempotent — safe to re-run).
  fixed = fixed.replace(
    /(-- [─\-]{20,}\s*\n-- \[\d+\/\d+\])/g,
    (match, _groups, offset) => {
      // Check if there's already a BEGIN TRANSACTION before this match
      const before = fixed.substring(Math.max(0, offset - 50), offset);
      if (before.includes("BEGIN TRANSACTION;")) {
        return match; // already has it, skip
      }
      return "BEGIN TRANSACTION;\n\n" + match;
    }
  );

  // Step 3b: Insert COMMIT TRANSACTION after each paper ends.
  //    A paper ends with keywords section: "...\nGO\n" then blank line, then next paper.
  //    Pattern: a GO line followed by a blank line followed by "BEGIN TRANSACTION;"
  //    We replace the blank line between GO and next BEGIN TRANSACTION with COMMIT.
  fixed = fixed.replace(
    /GO\n\n(BEGIN TRANSACTION;)/g,
    "GO\n\nCOMMIT TRANSACTION;\nGO\n\n$1"
  );

  // Step 3c: The FIRST paper's BEGIN TRANSACTION doesn't have a preceding COMMIT,
  //    but Step 3b might have added one if DECLARE @SourceID GO ends right before.
  //    Remove any COMMIT TRANSACTION that appears before the first paper.
  const firstPaperIdx = fixed.indexOf("-- [1/");
  const beforeFirst = fixed.substring(0, firstPaperIdx);
  if (beforeFirst.includes("COMMIT TRANSACTION;")) {
    fixed = beforeFirst.replace(/\nCOMMIT TRANSACTION;\nGO\n\n/g, "\n")
          + fixed.substring(firstPaperIdx);
  }

  // Step 3d: Add final COMMIT TRANSACTION after the last paper
  fixed = fixed.replace(
    /(\nPRINT '✅ Done!)/,
    "\nCOMMIT TRANSACTION;\nGO$1"
  );

  // 4. CRITICAL: Remove GO statements inside BEGIN/COMMIT TRANSACTION blocks.
  //    In SQL Server, GO destroys DECLARE variables. All sections within a paper
  //    (journal, field, paper, authors, keywords) must be in the SAME batch
  //    so that @JournalID, @FieldID, @PaperID, @SourceID survive across sections.
  const lines = fixed.split("\n");
  const result = [];
  let inTransaction = false;

  for (let i = 0; i < lines.length; i++) {
    const trimmed = lines[i].trim();

    if (trimmed === "BEGIN TRANSACTION;") {
      inTransaction = true;
      result.push(lines[i]);
      // Re-declare @SourceID inside each paper's batch (original global one was destroyed by GO)
      result.push("DECLARE @SourceID UNIQUEIDENTIFIER = (SELECT SourceID FROM API_SOURCE WHERE SourceName = 'OpenAlex');");
      continue;
    }

    if (trimmed === "COMMIT TRANSACTION;") {
      inTransaction = false;
      result.push(lines[i]);
      result.push("GO");
      result.push("");
      continue;
    }

    // Skip GO inside transactions
    if (inTransaction && trimmed === "GO") {
      if (result.length > 0 && result[result.length - 1].trim() !== "") {
        result.push("");
      }
      continue;
    }

    result.push(lines[i]);
  }
  fixed = result.join("\n");

  // 4b. Deduplicate DECLARE @AuthorID / @KeywordID inside each transaction.
  //     Since all sections are now in one batch, these can only be declared once.
  //     First occurrence stays as DECLARE; subsequent ones become empty (SET already follows).
  const lines2 = fixed.split("\n");
  const result2 = [];
  let inTx = false;
  let authorIdDeclared = false;
  let keywordIdDeclared = false;

  for (let i = 0; i < lines2.length; i++) {
    const trimmed = lines2[i].trim();

    if (trimmed === "BEGIN TRANSACTION;") {
      inTx = true;
      authorIdDeclared = false;
      keywordIdDeclared = false;
    }
    if (trimmed === "COMMIT TRANSACTION;") {
      inTx = false;
    }

    if (inTx && trimmed === "DECLARE @AuthorID UNIQUEIDENTIFIER;") {
      if (authorIdDeclared) continue; // skip duplicate
      authorIdDeclared = true;
    }
    if (inTx && trimmed === "DECLARE @AuthorID UNIQUEIDENTIFIER = NEWID();") {
      if (authorIdDeclared) {
        // Replace with SET @AuthorID = NEWID();
        result2.push("SET @AuthorID = NEWID();");
        continue;
      }
      authorIdDeclared = true;
    }
    if (inTx && trimmed === "DECLARE @KeywordID UNIQUEIDENTIFIER;") {
      if (keywordIdDeclared) continue; // skip duplicate
      keywordIdDeclared = true;
    }

    result2.push(lines2[i]);
  }
  fixed = result2.join("\n");

  // 5. Remove duplicate blank lines (cosmetic)
  fixed = fixed.replace(/\n{4,}/g, "\n\n\n");

  return fixed;
}

// ── Main ──

const args = process.argv.slice(2);

if (args.length === 0 || args.includes("--help") || args.includes("-h")) {
  console.log(`
Fix SQL tool — converts old giant-transaction SQL to per-paper transactions.

Usage:
  node fix-sql.js <input.sql> [output.sql]    Fix one file
  node fix-sql.js --all [directory]           Fix ALL .sql files in directory (default: ./output/)

Examples:
  node fix-sql.js papers_old.sql papers_fixed.sql
  node fix-sql.js --all
  node fix-sql.js --all D:/some/folder/
`);
  process.exit(0);
}

if (args.includes("--all")) {
  const dir = args.length > 1 ? args[args.indexOf("--all") + 1] : "./output";
  if (!statSync(dir, { throwIfNoEntry: false })?.isDirectory()) {
    console.error(`Error: directory not found: ${dir}`);
    process.exit(1);
  }

  const files = readdirSync(dir).filter(f => f.endsWith(".sql"));
  if (files.length === 0) {
    console.log(`No .sql files found in ${dir}`);
    process.exit(0);
  }

  console.log(`Found ${files.length} SQL file(s) in ${dir}:\n`);
  let fixedCount = 0;
  for (const file of files) {
    const filePath = join(dir, file);
    const content = readFileSync(filePath, "utf-8");

    if (content.includes("per-paper transaction")) {
      console.log(`  ⏭  ${file} — already fixed, skipping`);
      continue;
    }

    // Backup original
    const backupPath = filePath.replace(/\.sql$/, "_BACKUP_OLD.sql");
    renameSync(filePath, backupPath);

    const fixed = fixSqlRegex(content);
    writeFileSync(filePath, fixed, "utf-8");
    console.log(`  ✅ ${file} — fixed (backup: ${basename(backupPath)})`);
    fixedCount++;
  }
  console.log(`\nDone! ${fixedCount} file(s) fixed. Backups saved as *_BACKUP_OLD.sql`);
} else {
  const inputPath = args[0];
  const outputPath = args[1] || inputPath.replace(/\.sql$/, "_fixed.sql");

  let content;
  try {
    content = readFileSync(inputPath, "utf-8");
  } catch {
    console.error(`Error: cannot read ${inputPath}`);
    process.exit(1);
  }

  if (content.includes("per-paper transaction")) {
    console.log("File already in new format — nothing to fix.");
    process.exit(0);
  }

  const fixed = fixSqlRegex(content);
  writeFileSync(outputPath, fixed, "utf-8");
  console.log(`✅ Fixed → ${outputPath}`);
}
