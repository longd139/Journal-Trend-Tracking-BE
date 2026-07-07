# Auto-Streaming Importer — OpenAlex → SQL Server

Tự động import dữ liệu từ OpenAlex S3 snapshot vào SCITRACK SQL Server, thiết kế cho **PC dung lượng thấp (~5GB)**.

## 🎯 Vấn đề & Giải pháp

| Vấn đề | Giải pháp |
|--------|----------|
| Snapshot OpenAlex ~100GB | Tải **từng file một** (~50-200MB), import xong xóa ngay |
| Ngồi canh từng bước | **Tự động hóa 100%** — download → import → delete → repeat |
| Mất điện / mất mạng giữa chừng | **Resume tự động** — bỏ qua file đã import, tiếp tục file dang dở |
| Không nhớ đã chạy đến đâu | **Checkpoint** lưu mỗi 10 files + **log file** đầy đủ |

## 📋 Yêu cầu

| Thành phần | Cài đặt |
|-----------|---------|
| **AWS CLI** | `winget install Amazon.AWSCLI` hoặc [tải tại đây](https://aws.amazon.com/cli/) |
| **Python 3.9+** | `python --version` |
| **pyodbc** | `pip install pyodbc` |
| **ODBC Driver** | [Microsoft ODBC Driver 17+ for SQL Server](https://learn.microsoft.com/en-us/sql/connect/odbc/download-odbc-driver-for-sql-server) |
| **SQL Server** | Đã chạy `journal_trend_db.sql` để tạo schema |

## ⚡ Quick Start

```bash
cd tools/openalex-snapshot-importer

# 1. Cài dependencies (1 lần duy nhất)
pip install -r requirements.txt

# 2. Tạo file .env với DB info
echo DB_SERVER=localhost > .env
echo DB_PORT=1433 >> .env
echo DB_NAME=JournalTrendDB >> .env
echo DB_USER=sa >> .env
echo DB_PASSWORD=YourPassword123 >> .env

# 3. Test với 5 files trước
python auto_import.py --year-from 2026 --max-files 5

# 4. Nếu OK → chạy thật (có thể để qua đêm)
python auto_import.py --year-from 2024 --year-to 2026
```

## 🚀 Cách dùng

### Cơ bản

```bash
# Import 1 năm
python auto_import.py --year-from 2026

# Import nhiều năm
python auto_import.py --year-from 2024 --year-to 2026

# Giới hạn số lượng file (test)
python auto_import.py --year-from 2026 --max-files 20
```

### Quét trước khi import

```bash
# Xem có bao nhiêu file, tổng dung lượng (không import)
python auto_import.py --year-from 2024 --year-to 2026 --scan-only
```

Kết quả mẫu:
```
📊 Tổng: 450 files (~68.3 GB)
📋 10 file đầu tiên:
   part_000.gz (156.2 MB)
   part_001.gz (142.7 MB)
   ...
```

## 🔄 Cơ chế Resume

Khi script chạy, nó lưu checkpoint vào `.auto_import_checkpoint.json`:

```json
{
  "last_updated": "2026-07-08T03:15:30",
  "total_completed": 125,
  "completed_files": [
    "part_000.gz",
    "part_001.gz",
    ...
  ]
}
```

**Nếu bị ngắt giữa chừng** (mất điện, mất mạng, Ctrl+C):
```bash
# Chạy lại đúng lệnh cũ — tự động resume
python auto_import.py --year-from 2024 --year-to 2026
# → "📋 Checkpoint: 125 files đã hoàn thành từ lần trước"
# → "⏭ Bỏ qua 125 files đã import (checkpoint)"
# → Tiếp tục từ file thứ 126
```

**Muốn chạy lại từ đầu** (bỏ checkpoint):
```bash
# Xóa file checkpoint
rm .auto_import_checkpoint.json
# Hoặc trên Windows:
del .auto_import_checkpoint.json
```

## 📊 Output trong quá trình chạy

```
[1/450] (0.2%) part_000.gz (156.2 MB)
   ✅ OK — 3421 papers | Tổng: 3,421 papers | Đã xong: 1/450 files

[2/450] (0.4%) part_001.gz (142.7 MB)
   ✅ OK — 3105 papers | Tổng: 6,526 papers | Đã xong: 2/450 files

...

[10/450] (2.2%) part_009.gz (168.3 MB)
   ✅ OK — 3567 papers | Tổng: 34,210 papers | Đã xong: 10/450 files

   ── Checkpoint: 10/450 files | 34,210 papers | 3.2 min | 178.5 p/s ──
```

## 📄 File được tạo ra

| File | Mô tả |
|------|-------|
| `auto_import.log` | Log toàn bộ quá trình, kể cả lỗi |
| `.auto_import_checkpoint.json` | Checkpoint resume |
| `temp_snapshot/` | Thư mục tạm (chỉ chứa 1 file, tự xóa) |

## ⚠ Xử lý lỗi

| Lỗi | Nguyên nhân | Cách fix |
|-----|-----------|---------|
| `❌ AWS CLI chưa cài` | Chưa cài AWS CLI | `winget install Amazon.AWSCLI` |
| `❌ pyodbc chưa cài` | Thiếu dependency | `pip install pyodbc` |
| `❌ Cannot connect to SQL Server` | Sai DB config / SQL Server chưa chạy | Kiểm tra `.env`, chạy `docker compose up -d` |
| `❌ Download failed` | Mất mạng / S3 timeout | Script tự skip, file đó sẽ được retry lần sau |
| `❌ FAILED` trên 1 file | File corrupt / network | Chạy lại script để retry |

## ⏱ Ước tính thời gian

| Phạm vi | Số files | Số papers | Thời gian |
|---------|---------|-----------|-----------|
| 2026 | ~200 | ~500K-1M | ~1-2 giờ |
| 2025 | ~360 | ~2-4M | ~3-5 giờ |
| 2024 | ~360 | ~2-4M | ~3-5 giờ |
| 2023-2026 | ~1200 | ~8-12M | ~8-15 giờ |

> Tốc độ phụ thuộc vào: internet, CPU, SQL Server (SSD nhanh hơn HDD).

## 🔗 Sau khi import xong

```bash
# Bật backend Spring Boot, rồi rebuild Neo4j graph:
curl -X POST http://localhost:8080/api/v1/admin/sync/reindex-keywords

# Hoặc rebuild toàn bộ graph từ đầu:
curl -X POST http://localhost:8080/api/v1/admin/sync/rebuild-graph
```

## 🧹 Dọn dẹp

```bash
# Xóa checkpoint để chạy lại từ đầu
rm .auto_import_checkpoint.json

# Xóa log cũ
rm auto_import.log

# Xóa tất cả file tạm
rm -rf temp_snapshot/
```
