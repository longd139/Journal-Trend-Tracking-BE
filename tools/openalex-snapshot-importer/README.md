# OpenAlex Snapshot Importer

Import dữ liệu từ OpenAlex S3 snapshot vào SCITRACK SQL Server — tự động, không cần chạy tay.

## 🐍 Python script (khuyến nghị)

**`import_snapshot.py`** — import trực tiếp vào SQL Server, không sinh file SQL trung gian.

### Cài đặt

```bash
cd tools/openalex-snapshot-importer
pip install -r requirements.txt
```

### 🧪 Test ngay không cần snapshot

```bash
# Lấy 10 papers từ API import thẳng vào DB — kiểm tra pipeline hoạt động
python import_snapshot.py --test
```

### Cấu hình database

Tạo file `.env` trong thư mục tool:

```env
DB_SERVER=localhost
DB_PORT=1433
DB_NAME=SCITRACK
DB_USER=sa
DB_PASSWORD=YourPassword123!
```

Hoặc để trống `DB_PASSWORD` để dùng Windows Authentication.

### Import từ snapshot đã tải

```bash
# Import papers 2023-2026
python import_snapshot.py --input ./openalex-snapshot/data/works --year-from 2023 --year-to 2026

# Import 1 năm, giới hạn 50000 papers
python import_snapshot.py --input ./data/works --year 2024 --max-papers 50000
```

### Ưu điểm so với sinh SQL:

- ✅ **Không cần mở SSMS** — tự động connect và insert
- ✅ **Dedup thông minh** — load toàn bộ DOI hiện có vào RAM, check O(1)
- ✅ **Resume an toàn** — mỗi 500 papers commit 1 lần, lỗi chỉ rollback batch đó
- ✅ **Cache DOI ra disk** — nếu crash, lần sau chạy không bị duplicate
- ✅ **Auto retry** — tự skip file corrupt, tiếp tục file sau
- ✅ **Progress bar** — hiển thị papers/s, elapsed time

---

## 🟢 Node.js scripts (sinh SQL file — cách cũ)

Dùng nếu bạn muốn tự kiểm soát SQL trước khi chạy.

### `test-importer.js` — Test nhanh với API

```bash
node test-importer.js --count 5 --keyword "deep learning"
# → sinh file output/TEST_papers_5_*.sql
# → mở trong SSMS, F5 để chạy
```

### `importer.js` — Import toàn bộ từ snapshot

```bash
node importer.js --input ./openalex-snapshot/data/works --year-from 2023 --year-to 2026
# → sinh các file output/papers_batch_0001.sql, ...
# → chạy từng file trong SSMS
```

| | `scraper.js` (API) | `importer.js` (Snapshot) |
|---|---|---|
| Nguồn dữ liệu | OpenAlex REST API | File JSON Lines local |
| Giới hạn | **10k papers/query** | **Không giới hạn** |
| Rate limit | Có (700ms/request) | Không |
| Tốc độ | ~500 papers/phút | ~100k papers/phút |
| Dùng khi | Sync nhanh vài trăm papers | Import hàng triệu papers |

---

## Yêu cầu

- **Node.js** >= 18.0.0 (không cần cài thêm package nào — chỉ dùng built-in modules)
- **Ổ cứng**: ~50-100GB cho snapshot 3 năm (đã nén), ~300GB cho toàn bộ
- **SQL Server**: SCITRACK database đã được tạo sẵn (chạy `journal_trend_db.sql`)

---

## ⚡ Bước 0: Test pipeline với 5-10 papers (KHÔNG cần tải snapshot)

**Chạy test trước khi tải 100GB snapshot!** Script này gọi OpenAlex API (nhanh, miễn phí) để lấy vài papers thật, sinh 1 file SQL nhỏ để bạn test trên SQL Server:

```bash
cd tools/openalex-snapshot-importer

# Test với 5 papers (keyword mặc định: "machine learning")
node test-importer.js

# Test với keyword tuỳ chọn
node test-importer.js --count 10 --keyword "covid"

# Test với năm cụ thể
node test-importer.js --count 5 --keyword "deep learning" --year 2024
```

Sau khi chạy, mở file `output/TEST_papers_*.sql` trong SSMS và chạy F5. Nếu tất cả papers hiện `✅ OK` hoặc `⏭ SKIP` → schema DB đúng → sẵn sàng cho bước 1.

> Nếu gặp lỗi `❌ ERROR` — kiểm tra lại schema DB (`journal_trend_db.sql` đã chạy chưa? Có thiếu cột nào không?).

---

## Bước 1: Tải OpenAlex Snapshot

```bash
# Cài AWS CLI nếu chưa có
# Windows: tải từ https://aws.amazon.com/cli/
# Hoặc dùng winget: winget install Amazon.AWSCLI

# Tải TẤT CẢ works từ 2023 đến nay (~50-100GB)
aws s3 sync "s3://openalex/data/works/" "./openalex-snapshot/data/works/" \
  --no-sign-request \
  --exclude "*" \
  --include "updated_date=2023-*/*" \
  --include "updated_date=2024-*/*" \
  --include "updated_date=2025-*/*" \
  --include "updated_date=2026-*/*"
```

> ⚠ **Quan trọng**: Phải dùng `aws s3 sync` (không phải `cp`). Mỗi thư mục `updated_date=YYYY-MM-DD/` chứa nhiều file `.gz` — bạn cần TẤT CẢ.

> 💡 Nếu bị đứt giữa chừng, chạy lại lệnh cũ — `sync` sẽ tự động resume (chỉ tải file mới/thiếu).

### Kiểm tra dung lượng trước khi tải:

```bash
aws s3 ls --summarize --human-readable --no-sign-request --recursive "s3://openalex/data/works/" \
  --exclude "*" \
  --include "updated_date=2023-*/*" \
  --include "updated_date=2024-*/*" \
  --include "updated_date=2025-*/*" \
  --include "updated_date=2026-*/*"
```

---

## Bước 2: Chạy Importer

### 2.1 Xem thống kê trước (không sinh SQL)

```bash
cd tools/openalex-snapshot-importer
node importer.js --input ./openalex-snapshot/data/works --stats-only
```

Kết quả hiển thị: số file, tổng papers, phân bố theo năm, loại paper.

### 2.2 Import toàn bộ papers 2023-2026

```bash
node importer.js \
  --input ./openalex-snapshot/data/works \
  --year-from 2023 \
  --year-to 2026
```

### 2.3 Import 1 năm cụ thể

```bash
node importer.js --input ./data/works --year 2024
```

### 2.4 Test với số lượng nhỏ trước

```bash
# Chỉ lấy 5000 papers đầu tiên để test
node importer.js --input ./data/works --year-from 2023 --year-to 2026 --max-papers 5000
```

### 2.5 Tuỳ chỉnh kích thước batch

```bash
# 10000 papers mỗi file SQL, 1000 papers mỗi transaction
node importer.js --input ./data/works --year 2024 --batch-size 10000 --txn-size 1000
```

---

## Bước 3: Import SQL vào SQL Server

### Cách 1: Dùng SSMS / Azure Data Studio (khuyến nghị)

1. Mở SQL Server Management Studio hoặc Azure Data Studio
2. Kết nối đến SCITRACK database
3. Mở từng file SQL trong thư mục `output/`
4. Nhấn **F5** để chạy

### Cách 2: Dùng sqlcmd (command line)

```bash
sqlcmd -S localhost -d SCITRACK -U sa -P "YourPassword" -i output/papers_batch_0001.sql
```

### Cách 3: Chạy hàng loạt nhiều file (PowerShell)

```powershell
Get-ChildItem output/papers_batch_*.sql | Sort-Object Name | ForEach-Object {
    Write-Host "Running: $($_.Name)..."
    sqlcmd -S localhost -d SCITRACK -U sa -P "YourPassword" -i $_.FullName
}
```

---

## Bước 4: Sync Neo4j (sau SQL import)

Sau khi import xong vào SQL Server, chạy API để index lại Neo4j graph:

```bash
# Gọi API reindex (cần chạy BE Spring Boot trước)
curl -X POST http://localhost:8080/api/v1/admin/sync/reindex-keywords

# Hoặc nếu muốn clear Neo4j cũ và import lại từ đầu:
curl -X POST http://localhost:8080/api/v1/admin/sync/rebuild-graph
```

---

## Cấu trúc thư mục

```
tools/openalex-snapshot-importer/
├── importer.js          # Script chính
├── package.json         # Node.js config
├── README.md            # File này
├── output/              # File SQL đầu ra (tự động tạo)
│   ├── papers_batch_0001.sql
│   ├── papers_batch_0002.sql
│   └── import_stats.json
└── openalex-snapshot/   # Dữ liệu snapshot tải từ S3
    └── data/works/
        ├── updated_date=2023-01-01/
        │   ├── part_000.gz
        │   └── part_001.gz
        ├── updated_date=2023-01-02/
        └── ...
```

---

## Các tuỳ chọn dòng lệnh

| Flag | Mô tả | Mặc định |
|------|-------|---------|
| `-i, --input <dir>` | Thư mục chứa snapshot files | **(bắt buộc)** |
| `--year <YYYY>` | Chỉ import papers từ năm này | — |
| `--year-from <YYYY>` | Năm bắt đầu (inclusive) | — |
| `--year-to <YYYY>` | Năm kết thúc (inclusive) | — |
| `--batch-size <N>` | Số papers mỗi file SQL | 5000 |
| `--txn-size <N>` | Số papers mỗi transaction | 500 |
| `--max-papers <N>` | Dừng sau N papers (0 = không giới hạn) | 0 |
| `--stats-only` | Chỉ quét thống kê, không sinh SQL | false |
| `--dry-run` | Parse papers nhưng không ghi file | false |
| `-h, --help` | Hiển thị hướng dẫn | — |

---

## Xử lý lỗi thường gặp

### "Directory not found"
Đường dẫn `--input` không tồn tại. Kiểm tra lại đường dẫn đến thư mục snapshot.

### "No .gz or .jsonl files found"
Thư mục input không chứa file snapshot nào. Có thể bạn chưa tải snapshot hoặc tải sai cấu trúc.

### "SKIP_DOI" hoặc "SKIP_TITLE" trong SQL output
Đây là thông báo bình thường — paper trùng lặp được tự động bỏ qua. Không phải lỗi.

### File SQL quá lớn
Giảm `--batch-size` xuống (vd: 1000) để tạo nhiều file nhỏ hơn, dễ quản lý hơn.

### File `.gz` corrupt
Tool sẽ tự động bỏ qua file lỗi và tiếp tục. Nếu nhiều file lỗi, tải lại snapshot.

---

## Chiến lược đạt 1M papers

| Bước | Hành động | Kết quả ước tính |
|------|-----------|-----------------|
| 1 | Tải OpenAlex S3: `updated_date=2023*` đến `2026*` | 5-15M papers thô |
| 2 | `importer.js --year-from 2023 --year-to 2026` | Lọc ra ~3-8M papers |
| 3 | Chạy SQL files trên SQL Server | ~500k-1M papers/tháng gần đây |
| 4 | Chạy Neo4j rebuild graph | Graph search hoạt động |
| 5 | Bổ sung trending papers qua API sync (Semantic Scholar, CORE, arXiv) | Thêm papers mới nhất |

Với 1M papers mục tiêu, bạn chỉ cần chọn lọc `--year-from 2024 --year-to 2026` hoặc giới hạn `--max-papers 1000000` là đủ.

---

## Các nguồn dữ liệu khác

Sau khi có nền tảng từ OpenAlex snapshot, có thể bổ sung thêm:

| Nguồn | Cách lấy | Ghi chú |
|-------|---------|---------|
| **arXiv** | `s3://arxiv` (requester-pays, ~$0.09/GB) | ~2.5M preprints, cần AWS account |
| **Semantic Scholar** | API `bulkSyncFromSemanticScholar()` | Cần API key để tăng rate limit |
| **CORE** | API `bulkSyncFromCore()` | Cần API key, 60 rpm |

Các API sync đã có sẵn trong `DataSyncServiceImpl.java` và có thể gọi qua endpoint:
```bash
curl -X POST "http://localhost:8080/api/v1/admin/sync/bulk/semantic-scholar" \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"keywords": ["machine learning", "deep learning"], "papersPerKeyword": 50}'
```
