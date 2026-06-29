# OpenAlex Scraper — SCITRACK Data Pipeline

Standalone Node.js tool — không đụng vào code Java của hệ thống chính.

## Quick Start

```bash
cd tools/openalex-scraper

# ── SEARCH MODE ──

# Cào paper → JSON
node scraper.js --entity papers --query "machine learning" --limit 50

# Cào paper → SQL (chạy trực tiếp vào database)
node scraper.js --entity papers --query "machine learning" --limit 50 --format sql

# ── BATCH MODE (nhiều bài cùng lúc) ──

# Bằng OpenAlex ID
node scraper.js --entity batch --ids "W4292779060,W4302570518,W1234567890" --format sql

# Bằng URL đầy đủ (tự động parse lấy ID)
node scraper.js --entity batch --ids "https://openalex.org/works/W4292779060,https://openalex.org/works/W4302570518" --format sql

# Từ file chứa danh sách link (mỗi dòng 1 link)
node scraper.js --entity batch --ids-file my_links.txt --format sql
```

## Output

| Format | File | Mô tả |
|--------|------|-------|
| `json` | `./output/papers_<timestamp>.json` | JSON structured theo schema SCITRACK |
| `sql` | `./output/papers_<timestamp>.sql` | SQL script — mở SSMS chạy là xong |

## SQL Output: Cách hoạt động

Script SQL sinh ra dùng **upsert logic** để tránh trùng lặp:

| Bảng | Upsert Key | Hành vi |
|------|-----------|---------|
| `API_SOURCE` | SourceName = 'OpenAlex' | Chỉ INSERT nếu chưa có |
| `JOURNAL` | ISSN | Tìm ISSN có sẵn → dùng lại, nếu không → INSERT mới |
| `AUTHOR` | (SourceID, ExternalAuthorID) | Tìm External ID → dùng lại, nếu không → INSERT mới |
| `RESEARCH_PAPER` | DOI | Nếu DOI đã tồn tại → UPDATE JournalID/FieldID nếu NULL, không INSERT trùng |
| `KEYWORD` | NormalizedText | Tìm normalized text → dùng lại + tăng PaperCount, nếu không → INSERT mới |
| `PAPER_AUTHOR` | (PaperID, AuthorID) | Chỉ INSERT nếu chưa link |
| `PAPER_KEYWORD` | (PaperID, KeywordID) | Chỉ INSERT nếu chưa link |

⇒ **Bạn có thể chạy đi chạy lại file SQL nhiều lần, không sợ trùng dữ liệu.**

## API Rate Limit

| | Không email | Có email (polite pool) |
|---|---|---|
| Requests | ~10 req/s | ~100k req/ngày |
| Batch 100 papers | ~1 request | ~1 request |

Tool này đã được cấu hình sẵn email `longdpy130925@gmail.com` để dùng polite pool.

## Batch Mode: Cách hoạt động

1. Bạn đưa vào danh sách link/ID (tối đa bao nhiêu cũng được)
2. Tool tự động parse lấy OpenAlex ID từ mỗi link
3. Gửi 1 request API cho mỗi **50 papers** (giới hạn của OpenAlex filter)
4. Ví dụ: 200 papers → 4 requests (~1.5 giây)
5. Sinh ra 1 file `.sql` duy nhất
