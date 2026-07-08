#!/usr/bin/env python3
"""
OpenAlex Auto-Streaming Importer — dành cho PC dung lượng thấp (~5GB)

Tự động hóa TOÀN BỘ pipeline:
  1. Liệt kê file từ OpenAlex S3
  2. Tải TỪNG FILE MỘT → import vào SQL Server → xóa file
  3. Có checkpoint để resume nếu bị ngắt giữa chừng
  4. Log toàn bộ ra file + console

Yêu cầu:
  - AWS CLI đã cài đặt (chạy: aws --version)
  - Python + pyodbc đã cài (pip install -r requirements.txt)
  - File .env đã cấu hình DB connection

Cách dùng:
  # Import 2024-2026, tự chạy không cần canh
  python auto_import.py --year-from 2024 --year-to 2026

  # Chỉ 1 năm
  python auto_import.py --year-from 2026

  # Test với 5 file đầu tiên
  python auto_import.py --year-from 2026 --max-files 5

  # Resume từ checkpoint (tự động)
  python auto_import.py --year-from 2024 --year-to 2026
  # → tự bỏ qua các file đã import thành công

  # Chỉ scan xem có bao nhiêu file (không import)
  python auto_import.py --year-from 2024 --year-to 2026 --scan-only
"""

import argparse
import os
import subprocess
import sys
import time
import json
import shutil
import tempfile
from datetime import datetime
from pathlib import Path

# ═══════════════════════════════════════════════════════════════
#  Configuration
# ═══════════════════════════════════════════════════════════════

SCRIPT_DIR = Path(__file__).parent.resolve()
IMPORTER_SCRIPT = SCRIPT_DIR / "import_snapshot.py"
CHECKPOINT_FILE = SCRIPT_DIR / ".auto_import_checkpoint.json"
LOG_FILE = SCRIPT_DIR / "auto_import.log"
TEMP_DIR = SCRIPT_DIR / "temp_snapshot"

S3_BASE = "s3://openalex/data/jsonl/works/"


def log(msg, also_print=True):
    """Ghi log ra cả console và file."""
    timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    line = f"[{timestamp}] {msg}"
    if also_print:
        print(line)
    with open(LOG_FILE, "a", encoding="utf-8") as f:
        f.write(line + "\n")


def check_dependencies():
    """Kiểm tra AWS CLI và Python packages."""
    # Check AWS CLI
    try:
        subprocess.run(["aws", "--version"], capture_output=True, check=True, encoding="utf-8", errors="replace")
    except (subprocess.CalledProcessError, FileNotFoundError):
        log("❌ AWS CLI chưa được cài đặt.")
        log("   Tải từ: https://aws.amazon.com/cli/")
        log("   Hoặc: winget install Amazon.AWSCLI")
        sys.exit(1)

    # Check importer script
    if not IMPORTER_SCRIPT.exists():
        log(f"❌ Không tìm thấy {IMPORTER_SCRIPT}")
        log("   Hãy chạy script này trong thư mục tools/openalex-snapshot-importer/")
        sys.exit(1)

    # Check pyodbc
    try:
        import pyodbc
    except ImportError:
        log("❌ pyodbc chưa được cài. Chạy: pip install -r requirements.txt")
        sys.exit(1)

    log("✓ Dependencies OK")


def list_s3_files(year_from, year_to):
    """
    Liệt kê tất cả file .gz từ OpenAlex S3 trong khoảng năm.
    Trả về list các dict: {s3_path, filename, size_mb, date_prefix}
    """
    log(f"\n🔍 Đang liệt kê file từ OpenAlex S3 ({year_from} → {year_to})...")
    log("   (có thể mất 1-2 phút vì phải scan nhiều thư mục)\n")

    files = []

    for year in range(year_from, year_to + 1):
        for month in range(1, 13):
            month_str = f"{month:02d}"
            prefix = f"updated_date={year}-{month_str}-"

            log(f"   Đang quét {year}-{month_str}...", also_print=False)

            try:
                result = subprocess.run(
                    [
                        "aws", "s3", "ls",
                        f"{S3_BASE}{prefix}",
                        "--no-sign-request",
                        "--recursive",
                    ],
                    capture_output=True,
                    encoding="utf-8",
                    errors="replace",
                    timeout=120,
                )

                if result.returncode != 0:
                    continue

                month_files = []
                for line in result.stdout.strip().split("\n"):
                    if not line.strip():
                        continue
                    # Format: "2024-01-15 10:30:45  123456789 path/to/file.gz"
                    parts = line.split()
                    if len(parts) >= 4 and parts[3].endswith(".gz"):
                        size_bytes = int(parts[2])
                        s3_path = f"s3://openalex/{parts[3]}"
                        filename = parts[3].split("/")[-1]

                        month_files.append({
                            "s3_path": s3_path,
                            "filename": filename,
                            "size_mb": round(size_bytes / (1024 * 1024), 1),
                            "date_prefix": prefix,
                        })

                files.extend(month_files)
                log(f"   ✓ {year}-{month_str}: {len(month_files)} files", also_print=False)

            except subprocess.TimeoutExpired:
                log(f"   ⚠ {year}-{month_str}: timeout", also_print=False)
            except Exception as e:
                log(f"   ⚠ {year}-{month_str}: {e}", also_print=False)

    total_gb = sum(f["size_mb"] for f in files) / 1024
    log(f"\n📊 Tổng: {len(files)} files (~{total_gb:.1f} GB)")

    if not files:
        log("❌ Không tìm thấy file nào. Kiểm tra năm hoặc kết nối internet.")
        sys.exit(1)

    return files


def load_checkpoint():
    """Đọc checkpoint file để biết những file nào đã import thành công."""
    if not CHECKPOINT_FILE.exists():
        return set()

    try:
        with open(CHECKPOINT_FILE, "r") as f:
            data = json.load(f)
        done = set(data.get("completed_files", []))
        log(f"📋 Checkpoint: {len(done)} files đã hoàn thành từ lần trước")
        return done
    except (json.JSONDecodeError, KeyError):
        return set()


def save_checkpoint(completed_files):
    """Lưu checkpoint ra disk."""
    with open(CHECKPOINT_FILE, "w") as f:
        json.dump({
            "last_updated": datetime.now().isoformat(),
            "total_completed": len(completed_files),
            "completed_files": list(completed_files),
        }, f, indent=2)


def import_file(filepath, max_papers=0):
    """Gọi import_snapshot.py để import 1 file vào DB."""
    cmd = [sys.executable, str(IMPORTER_SCRIPT), "--input", str(filepath)]
    if max_papers > 0:
        cmd.extend(["--max-papers", str(max_papers)])
    result = subprocess.run(
        cmd,
        capture_output=True,
        encoding="utf-8",
        errors="replace",
        timeout=3600,  # 1 hour timeout for large files
    )

    # Parse output để lấy số papers
    papers = 0
    for line in result.stdout.split("\n"):
        if "Papers inserted:" in line:
            try:
                # Extract digits only (box-drawing chars break simple split)
                digits = "".join(c for c in line.split(":")[-1] if c.isdigit())
                if digits:
                    papers = int(digits)
            except ValueError:
                pass

    return {
        "success": result.returncode == 0,
        "papers": papers,
        "stdout": result.stdout,
        "stderr": result.stderr,
    }


def run(args):
    """Main logic."""
    log("╔══════════════════════════════════════════════════════╗")
    log("║  🚀 OpenAlex Auto-Streaming Importer               ║")
    log("║  PC dung lượng thấp — tải từng file, import, xóa   ║")
    log("╚══════════════════════════════════════════════════════╝")
    log("")

    # ── Check dependencies ──
    check_dependencies()

    # ── List files ──
    files = list_s3_files(args.year_from, args.year_to)

    if args.scan_only:
        log("\n✅ Scan-only mode — không import gì cả.")
        # Hiện top 10 file
        log("\n📋 10 file đầu tiên:")
        for f in files[:10]:
            log(f"   {f['filename']} ({f['size_mb']} MB)")
        return

    # ── Load checkpoint ──
    completed = load_checkpoint()

    # Lọc bỏ file đã hoàn thành (dùng s3_path làm key để tránh trùng filename)
    pending = [f for f in files if f["s3_path"] not in completed]
    skipped = len(files) - len(pending)

    if skipped > 0:
        log(f"⏭ Bỏ qua {skipped} files đã import (checkpoint)")

    if args.max_files and args.max_files < len(pending):
        log(f"🔒 Giới hạn: {args.max_files} files")
        pending = pending[:args.max_files]

    if not pending:
        log("\n✅ Tất cả files đã được import. Không có gì để làm.")
        return

    pending_gb = sum(f["size_mb"] for f in pending) / 1024
    log(f"\n🚀 Bắt đầu import {len(pending)} files (~{pending_gb:.1f} GB tổng, ~{pending[0]['size_mb']} MB/file)\n")

    # ── Tạo temp dir ──
    TEMP_DIR.mkdir(exist_ok=True)

    # ── Stats ──
    start_time = time.time()
    total_papers = 0
    success_count = 0
    fail_count = 0

    for i, f_info in enumerate(pending):
        idx = i + 1
        pct = round(idx / len(pending) * 100, 1)

        log(f"[{idx}/{len(pending)}] ({pct}%) {f_info['filename']} ({f_info['size_mb']} MB)")

        local_path = TEMP_DIR / f_info["filename"]
        import_result = None

        try:
            # Bước 1: Download
            log(f"   ⬇ Downloading...", also_print=False)
            dl_start = time.time()
            subprocess.run(
                ["aws", "s3", "cp", f_info["s3_path"], str(local_path), "--no-sign-request"],
                capture_output=True,
                check=True,
                encoding="utf-8",
                errors="replace",
                timeout=300,
            )
            dl_time = round(time.time() - dl_start, 1)

            if not local_path.exists():
                raise Exception("File không tồn tại sau khi download")

            actual_size = round(local_path.stat().st_size / (1024 * 1024), 1)
            log(f"   ⬇ Downloaded: {actual_size} MB ({dl_time}s)", also_print=False)

            # Bước 2: Import
            log(f"   📥 Importing...", also_print=False)
            import_result = import_file(local_path, args.max_papers_per_file)

            if import_result["success"]:
                total_papers += import_result["papers"]
                success_count += 1
                completed.add(f_info["s3_path"])
                log(f"   ✅ OK — {import_result['papers']} papers | "
                    f"Tổng: {total_papers:,} papers | "
                    f"Đã xong: {len(completed)}/{len(files)} files")
            else:
                fail_count += 1
                log(f"   ❌ FAILED")
                if import_result["stderr"]:
                    # Chỉ log dòng cuối của stderr
                    err_lines = [l for l in import_result["stderr"].split("\n") if l.strip()]
                    for err_line in err_lines[-3:]:
                        log(f"      {err_line}", also_print=False)

        except subprocess.CalledProcessError as e:
            fail_count += 1
            log(f"   ❌ Download failed: {e}")

        except Exception as e:
            fail_count += 1
            log(f"   ❌ Error: {e}")

        finally:
            # Luôn xóa file temp
            if local_path.exists():
                local_path.unlink()

        # ── Lưu checkpoint mỗi 10 files ──
        if idx % 10 == 0:
            save_checkpoint(completed)
            elapsed = round((time.time() - start_time) / 60, 1)
            rate = round(total_papers / (time.time() - start_time), 1) if total_papers > 0 else 0
            log(f"")
            log(f"   ── Checkpoint: {len(completed)}/{len(files)} files | "
                f"{total_papers:,} papers | {elapsed} min | {rate} p/s ──")
            log(f"")

    # ── Final checkpoint ──
    save_checkpoint(completed)

    # ── Cleanup ──
    if TEMP_DIR.exists():
        try:
            TEMP_DIR.rmdir()
        except OSError:
            pass

    # ── Summary ──
    total_time = round((time.time() - start_time) / 60, 1)
    avg_rate = round(total_papers / (time.time() - start_time), 1) if total_papers > 0 else 0

    log("")
    log("╔══════════════════════════════════════════════════════╗")
    log("║  📊 HOÀN THÀNH                                     ║")
    log("╠══════════════════════════════════════════════════════╣")
    log(f"║  Files processed:  {success_count:>5} OK / {fail_count:<5} fail       ║")
    log(f"║  Total papers:     {total_papers:>10,}                  ║")
    log(f"║  Total time:       {total_time:>10.1f} min              ║")
    log(f"║  Avg speed:        {avg_rate:>10.1f} papers/s          ║")
    log("╚══════════════════════════════════════════════════════╝")

    if total_papers > 0:
        log("")
        log(f"✅ Done! {total_papers:,} papers đã import vào DB.")
        log(f"📄 Log file: {LOG_FILE}")
        log(f"📋 Checkpoint: {CHECKPOINT_FILE}")
        log("")
        log("🔗 Tiếp theo — rebuild Neo4j graph:")
        log("   curl -X POST http://localhost:8080/api/v1/admin/sync/reindex-keywords")

    if fail_count > 0:
        log("")
        log(f"⚠ {fail_count} files bị lỗi. Chạy lại script để retry:")
        log(f"   python auto_import.py --year-from {args.year_from} --year-to {args.year_to}")


# ═══════════════════════════════════════════════════════════════
#  CLI
# ═══════════════════════════════════════════════════════════════

def main():
    parser = argparse.ArgumentParser(
        description="OpenAlex Auto-Streaming Importer — cho PC dung lượng thấp",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  # Import toàn bộ 2024-2026 (tự chạy, có resume)
  python auto_import.py --year-from 2024 --year-to 2026

  # Chỉ 1 năm
  python auto_import.py --year-from 2026

  # Test với 5 files đầu
  python auto_import.py --year-from 2026 --max-files 5

  # Chỉ scan số lượng file (không import)
  python auto_import.py --year-from 2024 --year-to 2026 --scan-only
        """,
    )

    parser.add_argument("--year-from", type=int, required=True, help="Năm bắt đầu (inclusive)")
    parser.add_argument("--year-to", type=int, help="Năm kết thúc (inclusive, mặc định = year-from)")
    parser.add_argument("--max-files", type=int, default=0, help="Giới hạn số file (0 = không giới hạn)")
    parser.add_argument("--max-papers-per-file", type=int, default=5000, help="Giới hạn papers mỗi file (default: 5000)")
    parser.add_argument("--scan-only", action="store_true", help="Chỉ scan, không import")

    args = parser.parse_args()

    if not args.year_to:
        args.year_to = args.year_from

    # Load .env nếu có
    env_path = SCRIPT_DIR / ".env"
    if env_path.exists():
        with open(env_path, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if line and not line.startswith("#") and "=" in line:
                    key, _, value = line.partition("=")
                    key = key.strip()
                    value = value.strip().strip('"').strip("'")
                    if key and value:
                        os.environ[key] = value

    run(args)


if __name__ == "__main__":
    main()
