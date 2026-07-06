#!/usr/bin/env python3
"""
OpenAlex Snapshot Importer v2.0 — SCITRACK Auto-Import Pipeline

Đọc OpenAlex S3 snapshot JSON Lines (.gz) và import TRỰC TIẾP vào SQL Server.
KHÔNG cần sinh file SQL trung gian, KHÔNG cần chạy tay trong SSMS.

Yêu cầu:
    pip install -r requirements.txt

Cách dùng:
    # Test với 10 papers trước (dùng API, không cần snapshot)
    python import_snapshot.py --test

    # Import toàn bộ snapshot 2023-2026
    python import_snapshot.py --input ./openalex-snapshot/data/works --year-from 2023 --year-to 2026

    # Import 1 năm, giới hạn 50000 papers
    python import_snapshot.py --input ./data/works --year 2024 --max-papers 50000

    # Dùng system environment — dùng chung env vars với Spring Boot:
    # DATABASE_HOST, DATABASE_PORT, DATABASE_NAME, DATABASE_USERNAME, DATABASE_PASSWORD
    python import_snapshot.py --input ./data/works --year 2024
"""

import argparse
import gzip
import json
import os
import sys
import time
from collections import defaultdict
from pathlib import Path

# ═══════════════════════════════════════════════════════════════
#  Configuration
# ═══════════════════════════════════════════════════════════════

MAX_AUTHORS_PER_PAPER = 5
MAX_KEYWORDS_PER_PAPER = 8
TITLE_MAX_LENGTH = 1000
BATCH_SIZE = 500          # Papers per DB transaction
CACHE_SAVE_INTERVAL = 50_000  # Save DOI cache to disk every N papers

# ═══════════════════════════════════════════════════════════════
#  Database connection
# ═══════════════════════════════════════════════════════════════


def get_connection():
    """Create SQL Server connection from system environment variables.

    Dùng chung env vars với Spring Boot (application.properties local):
      DATABASE_HOST     — host (default: localhost)
      DATABASE_PORT     — port (default: 1433)
      DATABASE_NAME     — database name (default: JournalTrendDB)
      DATABASE_USERNAME — SQL Server user (default: sa)
      DATABASE_PASSWORD — password (leave empty for Windows Auth)
    """
    import pyodbc

    server = os.environ.get("DATABASE_HOST", "localhost")
    port = os.environ.get("DATABASE_PORT", "1433")
    db = os.environ.get("DATABASE_NAME", "JournalTrendDB")
    user = os.environ.get("DATABASE_USERNAME", "sa")
    password = os.environ.get("DATABASE_PASSWORD", "")

    if not password:
        # Try Windows Auth
        conn_str = (
            f"DRIVER={{ODBC Driver 17 for SQL Server}};"
            f"SERVER={server},{port};"
            f"DATABASE={db};"
            f"Trusted_Connection=yes;"
            f"TrustServerCertificate=yes;"
        )
    else:
        conn_str = (
            f"DRIVER={{ODBC Driver 17 for SQL Server}};"
            f"SERVER={server},{port};"
            f"DATABASE={db};"
            f"UID={user};"
            f"PWD={password};"
            f"Encrypt=no;"
            f"TrustServerCertificate=yes;"
        )

    print(f"🔗 Connecting to {server}:{port}/{db} as {user if password else 'Windows Auth'}...")
    try:
        conn = pyodbc.connect(conn_str, timeout=10)
        conn.autocommit = False
        print("   ✓ Connected!")
        return conn
    except pyodbc.Error as e:
        # Try ODBC Driver 18
        if "ODBC Driver 17" in conn_str:
            conn_str = conn_str.replace("ODBC Driver 17", "ODBC Driver 18 for SQL Server")
            conn_str += "TrustServerCertificate=yes;"
            try:
                conn = pyodbc.connect(conn_str, timeout=10)
                conn.autocommit = False
                print("   ✓ Connected! (using ODBC Driver 18)")
                return conn
            except pyodbc.Error:
                pass
        print(f"\n❌ Cannot connect to SQL Server:")
        print(f"   {e}")
        print(f"\n💡 Troubleshooting:")
        print(f"   1. Is SQL Server running? Check Docker: docker compose ps")
        print(f"   2. Set env vars: DATABASE_HOST, DATABASE_PORT, DATABASE_NAME, DATABASE_USERNAME, DATABASE_PASSWORD")
        print(f"   3. Or use a .env file at the project root (Spring Boot style)")
        print(f"   4. Install ODBC Driver: https://learn.microsoft.com/en-us/sql/connect/odbc/download-odbc-driver-for-sql-server")
        sys.exit(1)


# ═══════════════════════════════════════════════════════════════
#  Data mapping — OpenAlex API → SCITRACK schema
# ═══════════════════════════════════════════════════════════════

def rebuild_abstract(inverted_index):
    if not inverted_index or not isinstance(inverted_index, dict):
        return None
    entries = list(inverted_index.items())
    if not entries:
        return None
    positions = []
    for word, indices in entries:
        for idx in indices:
            positions.append((idx, word))
    positions.sort(key=lambda x: x[0])
    return " ".join(p[1] for p in positions)


def normalize_doi(doi):
    if not doi:
        return None
    doi = doi.strip()
    return doi.replace("https://doi.org/", "", 1).replace("http://doi.org/", "", 1)


def trim_to_length(s, max_len):
    if not s:
        return None
    s = s.strip()
    return s[:max_len] if len(s) > max_len else s


def extract_journal(work):
    src = (work.get("primary_location") or {}).get("source")
    if not src or not src.get("display_name"):
        return None
    return {
        "display_name": trim_to_length(src["display_name"], 500),
        "issn": trim_to_length(src.get("issn_l"), 20),
        "publisher": trim_to_length(
            src.get("publisher") or src.get("host_organization_name"), 300
        ),
    }


def extract_authors(authorships, max_authors=MAX_AUTHORS_PER_PAPER):
    if not authorships:
        return []
    result = []
    for i, a in enumerate(authorships[:max_authors]):
        author_info = a.get("author") or {}
        result.append({
            "openalex_id": author_info.get("id"),
            "full_name": (
                author_info.get("display_name")
                or a.get("raw_author_name")
                or "Unknown Author"
            ),
            "affiliation": (
                (a.get("raw_affiliation_strings") or ["Unknown"])[0]
            ),
            "author_order": i + 1,
        })
    return result


def extract_keywords(work, max_keywords=MAX_KEYWORDS_PER_PAPER):
    seen = set()
    result = []

    for kw in (work.get("keywords") or []):
        text = kw.get("display_name") or kw.get("keyword")
        if text and text.lower() not in seen:
            seen.add(text.lower())
            result.append({"keyword": text, "score": kw.get("score", 1.0)})
            if len(result) >= max_keywords:
                return result

    for topic in (work.get("topics") or []):
        text = topic.get("display_name")
        if text and text.lower() not in seen:
            seen.add(text.lower())
            result.append({"keyword": text, "score": topic.get("score", 0.8)})
            if len(result) >= max_keywords:
                return result

    return result


def extract_research_field(work):
    topics = work.get("topics")
    if not topics:
        return None
    top = topics[0]
    field = top.get("field") or {}
    domain = top.get("domain") or {}
    return {
        "field_name": field.get("display_name") or domain.get("display_name"),
    }


def resolve_title(work):
    return work.get("display_name") or work.get("title") or "Untitled"


def resolve_pdf_url(work):
    best = work.get("best_oa_location") or work.get("primary_location") or {}
    if best.get("pdf_url"):
        return best["pdf_url"]
    oa = work.get("open_access") or {}
    if oa.get("oa_url"):
        return oa["oa_url"]
    return None


def map_paper(work):
    title = trim_to_length(resolve_title(work), TITLE_MAX_LENGTH)
    abstract = rebuild_abstract(work.get("abstract_inverted_index"))

    return {
        "openalex_id": work.get("id"),
        "title": title,
        "abstract": abstract,
        "doi": normalize_doi(work.get("doi")),
        "publication_date": work.get("publication_date"),
        "publication_year": work.get("publication_year"),
        "citation_count": work.get("cited_by_count", 0) or 0,
        "is_open_access": (work.get("open_access") or {}).get("is_oa", False),
        "pdf_url": resolve_pdf_url(work),
        "journal": extract_journal(work),
        "authors": extract_authors(work.get("authorships")),
        "keywords": extract_keywords(work),
        "research_field": extract_research_field(work),
        "type": work.get("type"),
    }


# ═══════════════════════════════════════════════════════════════
#  DOI Cache — tránh query DB cho mỗi paper
# ═══════════════════════════════════════════════════════════════

class DoiCache:
    """In-memory + on-disk cache of existing DOIs in the database."""

    def __init__(self, cache_file=".doi_cache.txt"):
        self.dois = set()
        self.new_dois = set()
        self.cache_file = cache_file

    def load_from_db(self, conn):
        """Load all existing DOIs from SQL Server (fast, single scan)."""
        print("📋 Loading existing DOIs from database...")
        cursor = conn.cursor()
        cursor.execute("SELECT DOI FROM RESEARCH_PAPER WHERE DOI IS NOT NULL")
        count = 0
        for row in cursor:
            if row[0]:
                self.dois.add(row[0].strip().lower())
                count += 1
            if count % 500_000 == 0 and count > 0:
                print(f"   ... {count:,} DOIs loaded")
        cursor.close()
        print(f"   ✓ {count:,} existing DOIs loaded")

        # Also load from disk cache
        if os.path.exists(self.cache_file):
            with open(self.cache_file, "r") as f:
                disk_count = 0
                for line in f:
                    doi = line.strip().lower()
                    if doi:
                        self.dois.add(doi)
                        disk_count += 1
            print(f"   ✓ {disk_count:,} DOIs from disk cache")

    def exists(self, doi):
        doi = (doi or "").strip().lower()
        if not doi:
            return False
        return doi in self.dois

    def add(self, doi):
        doi = (doi or "").strip().lower()
        if doi:
            self.dois.add(doi)
            self.new_dois.add(doi)

    def save(self):
        """Append new DOIs to disk cache."""
        if not self.new_dois:
            return
        with open(self.cache_file, "a") as f:
            for doi in self.new_dois:
                f.write(doi + "\n")
        self.new_dois.clear()

    def has_title_year(self, title, year):
        """Check title+year dedup in cache (but NOT in DB)."""
        return False  # Title+year is checked by DB unique constraint


# ═══════════════════════════════════════════════════════════════
#  Database inserter
# ═══════════════════════════════════════════════════════════════

class DatabaseInserter:
    """Handles all DB inserts with in-memory caching for related entities."""

    def __init__(self, conn):
        self.conn = conn
        self.cursor = conn.cursor()

        # In-memory caches (tránh query DB mỗi lần)
        self.journal_cache = {}     # (issn, name) → journal_id
        self.field_cache = {}        # field_name → field_id
        self.author_cache = {}       # (source_id, external_author_id) → author_id
        self.keyword_cache = {}      # normalized_text → keyword_id
        self.source_id = None        # OpenAlex source ID

        # Stats
        self.stats = {
            "papers_inserted": 0,
            "papers_skipped_doi": 0,
            "journals_created": 0,
            "fields_created": 0,
            "authors_created": 0,
            "keywords_created": 0,
        }

    def ensure_source(self):
        """Ensure 'OpenAlex' exists in API_SOURCE table."""
        self.cursor.execute(
            "SELECT SourceID FROM API_SOURCE WHERE SourceName = 'OpenAlex'"
        )
        row = self.cursor.fetchone()
        if row:
            self.source_id = row[0]
            return

        self.cursor.execute(
            "INSERT INTO API_SOURCE (SourceID, SourceName, BaseURL, IsActive, RateLimitRPM) "
            "VALUES (NEWID(), 'OpenAlex', 'https://api.openalex.org', 1, 100)"
        )
        self.conn.commit()
        self.cursor.execute(
            "SELECT SourceID FROM API_SOURCE WHERE SourceName = 'OpenAlex'"
        )
        self.source_id = self.cursor.fetchone()[0]
        print(f"   ✓ Created API_SOURCE 'OpenAlex' ({self.source_id})")

    def ensure_journal(self, journal):
        """Get or create journal, return journal_id (or None)."""
        if not journal:
            return None

        # Try cache first
        cache_key = (journal.get("issn") or "", (journal.get("display_name") or "").lower())
        if cache_key in self.journal_cache:
            return self.journal_cache[cache_key]

        # Try DB
        if journal.get("issn"):
            self.cursor.execute(
                "SELECT JournalID FROM JOURNAL WHERE ISSN = ?",
                (journal["issn"],)
            )
            row = self.cursor.fetchone()
            if row:
                self.journal_cache[cache_key] = row[0]
                return row[0]

        # Try by name
        name = journal["display_name"]
        self.cursor.execute(
            "SELECT TOP 1 JournalID FROM JOURNAL WHERE JournalName = ?",
            (name,)
        )
        row = self.cursor.fetchone()
        if row:
            self.journal_cache[cache_key] = row[0]
            return row[0]

        # Create new
        self.cursor.execute(
            "INSERT INTO JOURNAL (JournalID, SourceID, JournalName, ISSN, Publisher, IsActive) "
            "OUTPUT INSERTED.JournalID "
            "VALUES (NEWID(), ?, ?, ?, ?, 1)",
            (self.source_id, name, journal.get("issn"), journal.get("publisher"))
        )
        new_id = self.cursor.fetchone()[0]
        self.journal_cache[cache_key] = new_id
        self.stats["journals_created"] += 1
        return new_id

    def ensure_field(self, research_field):
        """Get or create research field, return field_id (or None)."""
        if not research_field or not research_field.get("field_name"):
            return None

        name_lower = research_field["field_name"].lower()
        if name_lower in self.field_cache:
            return self.field_cache[name_lower]

        self.cursor.execute(
            "SELECT TOP 1 FieldID FROM RESEARCH_FIELD WHERE FieldName = ?",
            (research_field["field_name"],)
        )
        row = self.cursor.fetchone()
        if row:
            self.field_cache[name_lower] = row[0]
            return row[0]

        self.cursor.execute(
            "INSERT INTO RESEARCH_FIELD (FieldID, FieldName, IsTracked) "
            "OUTPUT INSERTED.FieldID "
            "VALUES (NEWID(), ?, 1)",
            (research_field["field_name"],)
        )
        new_id = self.cursor.fetchone()[0]
        self.field_cache[name_lower] = new_id
        self.stats["fields_created"] += 1
        return new_id

    def ensure_author(self, author):
        """Get or create author, return author_id."""
        ext_id = author.get("openalex_id")
        ext_id_short = ext_id.split("/")[-1] if ext_id else None

        cache_key = (str(self.source_id), ext_id_short or author["full_name"])
        if cache_key in self.author_cache:
            return self.author_cache[cache_key]

        if ext_id_short:
            self.cursor.execute(
                "SELECT AuthorID FROM AUTHOR WHERE SourceID = ? AND ExternalAuthorID = ?",
                (self.source_id, ext_id_short)
            )
            row = self.cursor.fetchone()
            if row:
                self.author_cache[cache_key] = row[0]
                return row[0]

        # Create new
        self.cursor.execute(
            "INSERT INTO AUTHOR (AuthorID, SourceID, ExternalAuthorID, FullName, Affiliation, HIndex, TotalCitations) "
            "OUTPUT INSERTED.AuthorID "
            "VALUES (NEWID(), ?, ?, ?, ?, 0, 0)",
            (self.source_id, ext_id_short, author["full_name"], author.get("affiliation", "Unknown"))
        )
        new_id = self.cursor.fetchone()[0]
        self.author_cache[cache_key] = new_id
        self.stats["authors_created"] += 1
        return new_id

    def ensure_keyword(self, keyword_text, field_id):
        """Get or create keyword, return keyword_id."""
        norm = keyword_text.lower().strip()
        if norm in self.keyword_cache:
            # Increment paper count
            kid = self.keyword_cache[norm]
            self.cursor.execute(
                "UPDATE KEYWORD SET PaperCount = ISNULL(PaperCount, 0) + 1 WHERE KeywordID = ?",
                (kid,)
            )
            return kid

        self.cursor.execute(
            "SELECT KeywordID FROM KEYWORD WHERE NormalizedText = ?",
            (norm,)
        )
        row = self.cursor.fetchone()
        if row:
            self.keyword_cache[norm] = row[0]
            self.cursor.execute(
                "UPDATE KEYWORD SET PaperCount = ISNULL(PaperCount, 0) + 1 WHERE KeywordID = ?",
                (row[0],)
            )
            return row[0]

        self.cursor.execute(
            "INSERT INTO KEYWORD (KeywordID, FieldID, KeywordText, NormalizedText, PaperCount) "
            "OUTPUT INSERTED.KeywordID "
            "VALUES (NEWID(), ?, ?, ?, 1)",
            (field_id, keyword_text, norm)
        )
        new_id = self.cursor.fetchone()[0]
        self.keyword_cache[norm] = new_id
        self.stats["keywords_created"] += 1
        return new_id

    def insert_paper(self, paper, doi_cache):
        """Insert a single paper with all related entities. Returns True if inserted."""
        # DOI dedup
        if paper["doi"] and doi_cache.exists(paper["doi"]):
            self.stats["papers_skipped_doi"] += 1
            return False

        try:
            journal_id = self.ensure_journal(paper["journal"])
            field_id = self.ensure_field(paper["research_field"])

            # Insert paper
            self.cursor.execute(
                "INSERT INTO RESEARCH_PAPER (PaperID, SourceID, JournalID, FieldID, Title, Abstract, DOI, PubDate, PubYear, CitationCount, IsOpenAccess, PdfUrl) "
                "OUTPUT INSERTED.PaperID "
                "VALUES (NEWID(), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                (
                    self.source_id, journal_id, field_id,
                    paper["title"], paper["abstract"], paper["doi"],
                    paper["publication_date"],
                    paper["publication_year"],
                    paper["citation_count"],
                    1 if paper["is_open_access"] else 0,
                    paper["pdf_url"],
                )
            )
            paper_id = self.cursor.fetchone()[0]

            # Insert authors + junction
            for author in paper["authors"]:
                author_id = self.ensure_author(author)
                self.cursor.execute(
                    "IF NOT EXISTS (SELECT 1 FROM PAPER_AUTHOR WHERE PaperID = ? AND AuthorID = ?) "
                    "INSERT INTO PAPER_AUTHOR (PaperID, AuthorID, AuthorOrder, IsCorresponding) "
                    "VALUES (?, ?, ?, 0)",
                    (paper_id, author_id, paper_id, author_id, author["author_order"])
                )

            # Insert keywords + junction
            for kw in paper["keywords"]:
                kw_id = self.ensure_keyword(kw["keyword"], field_id)
                self.cursor.execute(
                    "IF NOT EXISTS (SELECT 1 FROM PAPER_KEYWORD WHERE PaperID = ? AND KeywordID = ?) "
                    "INSERT INTO PAPER_KEYWORD (PaperID, KeywordID, RelevanceScore) "
                    "VALUES (?, ?, ?)",
                    (paper_id, kw_id, paper_id, kw_id, kw.get("score", 1.0))
                )

            # Track DOI
            if paper["doi"]:
                doi_cache.add(paper["doi"])

            self.stats["papers_inserted"] += 1
            return True

        except Exception as e:
            # Rollback this paper only (caller handles transaction)
            raise


# ═══════════════════════════════════════════════════════════════
#  File processing
# ═══════════════════════════════════════════════════════════════

def discover_files(input_path):
    """Find all .gz files recursively."""
    root = Path(input_path)
    if not root.exists():
        print(f"❌ Directory not found: {input_path}")
        sys.exit(1)

    if root.is_file():
        return [str(root)]

    files = sorted(
        str(p) for p in root.rglob("*.gz")
        if p.is_file()
    )
    return files


def stream_papers_from_file(filepath):
    """Generator: yield one OpenAlex work dict at a time from a .gz file."""
    open_func = gzip.open if filepath.endswith(".gz") else open
    try:
        with open_func(filepath, "rt", encoding="utf-8", errors="replace") as f:
            for line_num, line in enumerate(f, 1):
                line = line.strip()
                if not line:
                    continue
                try:
                    work = json.loads(line)
                    if work and work.get("id"):
                        yield work
                except json.JSONDecodeError:
                    if line_num <= 3:
                        print(f"  ⚠ Parse error at line {line_num} in {Path(filepath).name}")
                    continue
    except (gzip.BadGzipFile, EOFError, OSError) as e:
        print(f"  ⚠ Corrupt file, skipping: {Path(filepath).name} — {e}")


# ═══════════════════════════════════════════════════════════════
#  Test mode — fetch papers from API instead of snapshot
# ═══════════════════════════════════════════════════════════════

def fetch_test_papers(keyword="machine learning", count=10, year=None):
    """Fetch a few papers from OpenAlex API for testing."""
    import urllib.request
    import urllib.parse

    base_url = "https://api.openalex.org/works"
    select = ",".join([
        "id", "doi", "title", "display_name",
        "publication_year", "publication_date", "cited_by_count",
        "abstract_inverted_index", "open_access",
        "primary_location", "best_oa_location",
        "topics", "keywords", "authorships",
        "referenced_works_count", "type"
    ])
    params = {
        "search": keyword,
        "sort": "cited_by_count:desc",
        "per-page": min(count, 200),
        "select": select,
    }
    if year:
        params["filter"] = f"from_publication_date:{year}-01-01,to_publication_date:{year}-12-31"

    url = f"{base_url}?{urllib.parse.urlencode(params)}"
    print(f"\n🌐 Fetching {count} test papers from OpenAlex API...")
    print(f"   Keyword: \"{keyword}\"{' | Year: ' + str(year) if year else ''}")

    req = urllib.request.Request(url, headers={"User-Agent": "SCITRACK-TestImporter/1.0"})
    with urllib.request.urlopen(req, timeout=30) as resp:
        data = json.loads(resp.read().decode())

    papers = [map_paper(w) for w in (data.get("results") or [])]
    print(f"   ✓ Got {len(papers)} papers (total available: {data.get('meta', {}).get('count', 0):,})")

    for i, p in enumerate(papers):
        title = (p["title"] or "Untitled")[:70]
        print(f"   {i+1}. [{p['publication_year'] or '?'}] {title}")

    return papers


# ═══════════════════════════════════════════════════════════════
#  Main import logic
# ═══════════════════════════════════════════════════════════════

def import_snapshot(args):
    conn = get_connection()
    inserter = DatabaseInserter(conn)
    doi_cache = DoiCache()

    # Ensure source exists
    inserter.ensure_source()

    # Load existing DOIs
    doi_cache.load_from_db(conn)

    # Collect papers to import
    papers = []
    is_test_mode = args.test

    if is_test_mode:
        papers = fetch_test_papers(
            keyword=args.test_keyword,
            count=args.max_papers if args.max_papers > 0 else 10,
            year=args.year_from or args.year_to,
        )
        if not papers:
            print("❌ No test papers returned. Check your internet connection.")
            sys.exit(1)
        total_files = 1
    else:
        if not args.input:
            print("❌ --input <directory> is required (or use --test for test mode)")
            sys.exit(1)
        files = discover_files(args.input)
        total_files = len(files)
        if total_files == 0:
            print(f"❌ No .gz files found in {args.input}")
            sys.exit(1)

        total_size_gb = sum(
            os.path.getsize(f) for f in files if os.path.exists(f)
        ) / (1024 ** 3)
        print(f"📂 Found {total_files} files ({total_size_gb:.1f} GB)")
        if args.year_from or args.year_to:
            print(f"   Year filter: {args.year_from or 'any'} → {args.year_to or 'any'}")
        if args.max_papers > 0:
            print(f"   Max papers: {args.max_papers:,}")
        print()

    # Progress tracking
    total_parsed = 0
    total_filtered = 0
    total_skipped = 0
    start_time = time.time()
    batch_papers = []
    year_dist = defaultdict(int)

    def flush_batch():
        """Commit current batch to DB."""
        nonlocal total_skipped
        if not batch_papers:
            return

        conn.autocommit = False
        batch_ok = 0
        for paper in batch_papers:
            try:
                if inserter.insert_paper(paper, doi_cache):
                    batch_ok += 1
                else:
                    total_skipped += 1
            except Exception as e:
                conn.rollback()
                print(f"\n  ⚠ Paper insert failed: {e}")
                # Re-create cursor after rollback
                inserter.cursor = conn.cursor()
                continue

        conn.commit()
        batch_papers.clear()

        # Save DOI cache periodically
        if inserter.stats["papers_inserted"] % CACHE_SAVE_INTERVAL == 0:
            doi_cache.save()

    # Process papers
    if is_test_mode:
        # Test mode — papers already in memory
        for work in papers:
            batch_papers.append(work)
            if len(batch_papers) >= BATCH_SIZE:
                flush_batch()
        flush_batch()
    else:
        # Full mode — stream from files
        for fi, filepath in enumerate(files, 1):
            fname = Path(filepath).name
            fsize = os.path.getsize(filepath) / (1024 ** 2)

            print(f"📄 [{fi}/{total_files}] {fname} ({fsize:.0f} MB)")

            file_count = 0
            for work in stream_papers_from_file(filepath):
                total_parsed += 1

                # Year filter
                py = work.get("publication_year")
                if args.year_from and (py is None or py < args.year_from):
                    total_filtered += 1
                    continue
                if args.year_to and (py is None or py > args.year_to):
                    total_filtered += 1
                    continue

                paper = map_paper(work)
                year_dist[paper["publication_year"] or "unknown"] += 1
                batch_papers.append(paper)

                if len(batch_papers) >= BATCH_SIZE:
                    flush_batch()

                # Progress
                inserted = inserter.stats["papers_inserted"]
                if inserted > 0 and inserted % 1000 == 0:
                    elapsed = time.time() - start_time
                    rate = inserted / elapsed if elapsed > 0 else 0
                    print(f"\r   📊 {inserted:,} papers | "
                          f"{rate:.0f} papers/s | "
                          f"parsed: {total_parsed:,} | "
                          f"skipped: {inserter.stats['papers_skipped_doi']:,} | "
                          f"{elapsed:.0f}s",
                          end="", flush=True)

                # Max papers limit
                if args.max_papers > 0 and inserted >= args.max_papers:
                    break

            # Flush remaining papers after each file
            flush_batch()
            print(f"\r   ✓ {inserter.stats['papers_inserted']:,} papers total after {fname}")

            if args.max_papers > 0 and inserter.stats["papers_inserted"] >= args.max_papers:
                print(f"\n⏹ Reached max papers limit ({args.max_papers:,})")
                break

    print()  # newline after progress

    # Flush remaining
    flush_batch()
    doi_cache.save()

    # ── Summary ──
    elapsed = time.time() - start_time
    inserted = inserter.stats["papers_inserted"]
    rate = inserted / elapsed if elapsed > 0 else 0

    print("\n╔══════════════════════════════════════════════════════╗")
    print("║  📊 IMPORT RESULTS                                  ║")
    print("╠══════════════════════════════════════════════════════╣")
    print(f"║  Papers inserted:    {inserted:>10,}                  ║")
    print(f"║  Duplicates skipped: {inserter.stats['papers_skipped_doi']:>10,}                  ║")
    print(f"║  Year filtered out:  {total_filtered:>10,}                  ║")
    print(f"║  Journals created:   {inserter.stats['journals_created']:>10,}                  ║")
    print(f"║  Fields created:     {inserter.stats['fields_created']:>10,}                  ║")
    print(f"║  Authors created:    {inserter.stats['authors_created']:>10,}                  ║")
    print(f"║  Keywords created:   {inserter.stats['keywords_created']:>10,}                  ║")
    print(f"║  Time:               {elapsed:>10.1f}s                 ║")
    print(f"║  Speed:              {rate:>10.0f} papers/s          ║")
    print("╠══════════════════════════════════════════════════════╣")
    print("║  📅 Top years:                                     ║")
    for year, count in sorted(year_dist.items(), key=lambda x: x[1], reverse=True)[:10]:
        y = str(year)
        print(f"║    {y:<8} {count:>10,}                            ║")
    print("╚══════════════════════════════════════════════════════╝")

    conn.close()

    if inserted > 0:
        print(f"\n✅ Done! {inserted:,} papers imported in {elapsed:.0f}s")
        print(f"\n🔗 Next: Trigger Neo4j reindex via API:")
        print(f"   curl -X POST http://localhost:8080/api/v1/admin/sync/reindex-keywords")


# ═══════════════════════════════════════════════════════════════
#  CLI
# ═══════════════════════════════════════════════════════════════

def main():
    parser = argparse.ArgumentParser(
        description="OpenAlex Snapshot Importer — auto-import vào SQL Server",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  # TEST MODE: lấy 10 papers từ API import thẳng vào DB
  python import_snapshot.py --test

  # Import toàn bộ snapshot
  python import_snapshot.py --input ./openalex-snapshot/data/works --year-from 2023 --year-to 2026

  # Import 1 năm, giới hạn 50000 papers
  python import_snapshot.py --input ./data/works --year 2024 --max-papers 50000

DB config (dùng system environment — chung với Spring Boot):
  DATABASE_HOST     — SQL Server host (default: localhost)
  DATABASE_PORT     — SQL Server port (default: 1433)
  DATABASE_NAME     — Database name (default: JournalTrendDB)
  DATABASE_USERNAME — Username (default: sa)
  DATABASE_PASSWORD — Password (leave empty for Windows Auth)
        """,
    )

    parser.add_argument("--input", "-i", help="Thư mục chứa OpenAlex snapshot .gz files")
    parser.add_argument("--test", action="store_true", help="Test mode: lấy vài papers từ API để test")
    parser.add_argument("--test-keyword", default="machine learning", help="Keyword cho test mode")
    parser.add_argument("--year-from", type=int, help="Năm bắt đầu (inclusive)")
    parser.add_argument("--year-to", type=int, help="Năm kết thúc (inclusive)")
    parser.add_argument("--year", type=int, help="Chỉ import 1 năm cụ thể")
    parser.add_argument("--max-papers", type=int, default=0, help="Dừng sau N papers (0 = không giới hạn)")
    parser.add_argument("--batch-size", type=int, default=BATCH_SIZE, help=f"Số papers mỗi transaction (default: {BATCH_SIZE})")

    args = parser.parse_args()

    # If --year is specified, set both from/to
    if args.year:
        args.year_from = args.year
        args.year_to = args.year

    # Validate
    if not args.test and not args.input:
        parser.error("Phải chỉ định --input <dir> hoặc dùng --test để test")

    # Run
    print("╔══════════════════════════════════════════════════════╗")
    if args.test:
        print("║  🧪 TEST MODE — OpenAlex Snapshot Importer v2.0     ║")
    else:
        print("║  🚀 OpenAlex Snapshot Importer v2.0                 ║")
    print("║  SCITRACK Auto-Import Pipeline                      ║")
    print("╚══════════════════════════════════════════════════════╝")

    import_snapshot(args)


if __name__ == "__main__":
    main()
