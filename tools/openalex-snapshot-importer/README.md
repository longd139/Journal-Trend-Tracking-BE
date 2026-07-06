# OpenAlex Snapshot Importer

Import dữ liệu từ OpenAlex S3 snapshot vào SQL Server — tự động, không cần sinh file SQL trung gian.

Dùng script Python **`import_snapshot.py`** — kết nối trực tiếp tới DB, insert theo batch.

## Cài đặt

```bash
cd tools/openalex-snapshot-importer
pip install -r requirements.txt
```

Cần ODBC Driver cho SQL Server:
- **Windows**: thường đã có sẵn, nếu chưa thì tải [ODBC Driver 17/18](https://learn.microsoft.com/en-us/sql/connect/odbc/download-odbc-driver-for-sql-server)
- **Linux/macOS**: cài `unixodbc` + `msodbcsql17` hoặc `msodbcsql18`

## Cấu hình database

Dùng system environment variables — chung với Spring Boot (`src/main/resources/application.properties`):

| Biến | Mô tả | Default |
|------|-------|---------|
| `DATABASE_HOST` | SQL Server host | `localhost` |
| `DATABASE_PORT` | Port | `1433` |
| `DATABASE_NAME` | Database name | `JournalTrendDB` |
| `DATABASE_USERNAME` | Username | `sa` |
| `DATABASE_PASSWORD` | Password | *(Windows Auth nếu để trống)* |

Có thể set trực tiếp trong terminal hoặc để trong file `.env` ở thư mục gốc project (Spring Boot tự load khi chạy).

---

## Cách dùng

### 1. Chạy thử (test mode) — không cần snapshot

Trước khi tải snapshot, kiểm tra pipeline bằng cách lấy vài papers từ OpenAlex API:

```bash
# Test với 10 papers mặc định (keyword: "machine learning")
python import_snapshot.py --test

# Test với keyword và số lượng tuỳ chỉnh
python import_snapshot.py --test --test-keyword "deep learning" --max-papers 20

# Test với năm cụ thể
python import_snapshot.py --test --test-keyword "covid" --max-papers 10 --year 2024
```

Script sẽ:
1. Gọi OpenAlex API lấy N papers thật
2. Insert trực tiếp vào SQL Server
3. Hiển thị kết quả (bao nhiêu inserted, skipped)

Nếu thấy `✓ Connected` và `Papers inserted > 0` → pipeline OK, sẵn sàng chạy thật.

### 2. Tải OpenAlex Snapshot

```bash
# Cài AWS CLI nếu chưa có
# Windows: winget install Amazon.AWSCLI

# Tải works từ 2023 đến nay
aws s3 sync "s3://openalex/data/works/" "./openalex-snapshot/data/works/" \
  --no-sign-request \
  --exclude "*" \
  --include "updated_date=2023-*/*" \
  --include "updated_date=2024-*/*" \
  --include "updated_date=2025-*/*" \
  --include "updated_date=2026-*/*"
```

> Dùng `aws s3 sync` (không phải `cp`) — nếu bị đứt giữa chừng, chạy lại sẽ resume.

### 3. Import thật

```bash
# Import papers 2023-2026
python import_snapshot.py --input ./openalex-snapshot/data/works --year-from 2023 --year-to 2026

# Import 1 năm
python import_snapshot.py --input ./data/works --year 2024

# Import có giới hạn (test trước với 5000 papers)
python import_snapshot.py --input ./data/works --year-from 2023 --year-to 2026 --max-papers 5000

# Import với batch size tuỳ chỉnh (mặc định 500 papers/transaction)
python import_snapshot.py --input ./data/works --year 2024 --batch-size 1000
```

## Tham số CLI

| Flag | Mô tả | Default |
|------|-------|---------|
| `-i, --input <dir>` | Thư mục chứa snapshot .gz files | *(bắt buộc nếu không --test)* |
| `--test` | Test mode: fetch từ API, không cần snapshot | — |
| `--test-keyword <keyword>` | Keyword cho test mode | `machine learning` |
| `--year <YYYY>` | Chỉ import papers từ năm này | — |
| `--year-from <YYYY>` | Năm bắt đầu (inclusive) | — |
| `--year-to <YYYY>` | Năm kết thúc (inclusive) | — |
| `--max-papers <N>` | Dừng sau N papers (0 = unlimited) | `0` |
| `--batch-size <N>` | Số papers mỗi DB transaction | `500` |

## Sau khi import

Chạy API để index lại Neo4j graph (cần BE Spring Boot đang chạy):

```bash
curl -X POST http://localhost:8080/api/v1/admin/sync/reindex-keywords
```

## Cấu trúc thư mục

```
tools/openalex-snapshot-importer/
├── import_snapshot.py    # Script chính (Python)
├── requirements.txt      # Python dependencies
├── README.md             # File này
└── openalex-snapshot/    # Dữ liệu snapshot tải từ S3
    └── data/works/
        ├── updated_date=2023-01-01/
        │   ├── part_000.gz
        │   └── part_001.gz
        ├── updated_date=2023-01-02/
        └── ...
```

## Xử lý lỗi thường gặp

| Lỗi | Nguyên nhân | Cách fix |
|-----|-------------|----------|
| `Cannot connect to SQL Server` | Sai host/port/user/password | Kiểm tra env vars, docker compose |
| `No .gz files found` | Sai đường dẫn `--input` | Kiểm tra thư mục snapshot |
| `Paper insert failed` | Lỗi dữ liệu (quá dài, null...) | Tự động skip và log ra console |

## Đặc điểm

- **Insert trực tiếp** — không sinh file SQL trung gian
- **Dedup bằng DOI** — load toàn bộ DOI hiện có vào RAM, check O(1)
- **Commit theo batch** — mỗi N papers commit 1 lần, lỗi chỉ rollback batch đó
- **Cache DOI ra disk** — nếu crash, lần sau không bị duplicate
- **Auto skip** file corrupt, tiếp tục file sau
