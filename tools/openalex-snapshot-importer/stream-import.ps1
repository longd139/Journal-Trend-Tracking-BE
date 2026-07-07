#!/usr/bin/env pwsh
<#
  OpenAlex Streaming Importer — dành cho PC dung lượng thấp (~5GB)

  Cách hoạt động:
  1. Dùng aws s3 ls để liệt kê tất cả file .gz trong khoảng năm chỉ định
  2. Tải TỪNG FILE MỘT bằng aws s3 cp
  3. Import file đó vào SQL Server bằng import_snapshot.py
  4. Xóa file .gz vừa tải để giải phóng dung lượng
  5. Lặp lại cho đến khi hết tất cả file

  Yêu cầu:
  - AWS CLI đã cài đặt (không cần AWS account — snapshot OpenAlex miễn phí)
  - Python + pyodbc đã cài (pip install -r requirements.txt)
  - File .env đã cấu hình DB connection

  Cách dùng:
  .\stream-import.ps1 -YearFrom 2024 -YearTo 2026

  # Hoặc chỉ 1 năm:
  .\stream-import.ps1 -YearFrom 2026

  # Test với 10 file đầu tiên:
  .\stream-import.ps1 -YearFrom 2026 -MaxFiles 10
#>

param(
    [Parameter(Mandatory=$true)]
    [int]$YearFrom,

    [int]$YearTo = $YearFrom,

    [int]$MaxFiles = 0,          # 0 = không giới hạn

    [string]$TempDir = "./temp_snapshot",

    [string]$ImporterScript = "./import_snapshot.py"
)

$ErrorActionPreference = "Stop"

# ═══════════════════════════════════════════════════════════════
# Kiểm tra dependencies
# ═══════════════════════════════════════════════════════════════

Write-Host "╔══════════════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║  📦 OpenAlex Streaming Importer                     ║" -ForegroundColor Cyan
Write-Host "║  PC dung luong thap (~5GB) — tai tung file mot      ║" -ForegroundColor Cyan
Write-Host "╚══════════════════════════════════════════════════════╝" -ForegroundColor Cyan
Write-Host ""

# Check AWS CLI
$awsExists = Get-Command aws -ErrorAction SilentlyContinue
if (-not $awsExists) {
    Write-Host "❌ AWS CLI chua duoc cai dat." -ForegroundColor Red
    Write-Host "   Tai tu: https://aws.amazon.com/cli/" -ForegroundColor Yellow
    Write-Host "   Hoac dung winget: winget install Amazon.AWSCLI" -ForegroundColor Yellow
    exit 1
}

# Check Python
$pythonExists = Get-Command python -ErrorAction SilentlyContinue
if (-not $pythonExists) {
    Write-Host "❌ Python chua duoc cai dat." -ForegroundColor Red
    exit 1
}

# Check importer script
if (-not (Test-Path $ImporterScript)) {
    Write-Host "❌ Khong tim thay $ImporterScript" -ForegroundColor Red
    Write-Host "   Hay chay script nay trong thu muc tools/openalex-snapshot-importer/" -ForegroundColor Yellow
    exit 1
}

# ═══════════════════════════════════════════════════════════════
# Tạo temp directory
# ═══════════════════════════════════════════════════════════════

if (-not (Test-Path $TempDir)) {
    New-Item -ItemType Directory -Path $TempDir -Force | Out-Null
}
Write-Host "📁 Thu muc tam: $TempDir" -ForegroundColor Gray

# ═══════════════════════════════════════════════════════════════
# Bước 1: Liệt kê tất cả file từ S3
# ═══════════════════════════════════════════════════════════════

Write-Host ""
Write-Host "🔍 Dang liet ke cac file tu OpenAlex S3..." -ForegroundColor Yellow
Write-Host "   Nam: $YearFrom → $YearTo" -ForegroundColor Gray

$s3BasePath = "s3://openalex/data/works/"
$allFiles = @()

# AWS CLI chi cho phep 1 --include pattern, nen phai goi tung nam
for ($year = $YearFrom; $year -le $YearTo; $year++) {
    # Liet ke tung thang
    for ($month = 1; $month -le 12; $month++) {
        $monthStr = "{0:D2}" -f $month

        # Chi can list 1 file de biet thang nay co data khong
        # Neu co, lay tat ca file trong thang do
        $s3Prefix = "updated_date=$year-$monthStr-"

        Write-Host "   Đang quet $year-$monthStr..." -ForegroundColor Gray -NoNewline

        try {
            $result = aws s3 ls "$s3BasePath$s3Prefix" --no-sign-request --recursive 2>$null | Out-String

            if ($result -and $result.Trim().Length -gt 0) {
                $lines = $result -split "`n" | Where-Object { $_.Trim() -ne "" }
                foreach ($line in $lines) {
                    # Format: "2024-01-15 10:30:45  123456789 path/to/file.gz"
                    if ($line -match '\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\s+\d+\s+(.+\.gz)') {
                        $filePath = $matches[1]
                        $sizeBytes = ($line -split '\s+')[2]
                        $allFiles += @{
                            S3Path = "s3://openalex/$filePath"
                            SizeMB = [math]::Round([int64]$sizeBytes / 1MB, 1)
                            DatePrefix = $s3Prefix
                        }
                    }
                }
                $fileCount = ($allFiles | Where-Object { $_.DatePrefix -eq $s3Prefix }).Count
                Write-Host " ✓ $fileCount files" -ForegroundColor Green
            } else {
                Write-Host " (trong)" -ForegroundColor Gray
            }
        } catch {
            Write-Host " ⚠ loi" -ForegroundColor DarkYellow
        }
    }
}

$totalSizeGB = [math]::Round(($allFiles | Measure-Object -Property SizeMB -Sum).Sum / 1024, 1)
Write-Host ""
Write-Host "📊 Tong cong: $($allFiles.Count) files (~$totalSizeGB GB)" -ForegroundColor Cyan

if ($allFiles.Count -eq 0) {
    Write-Host "❌ Khong tim thay file nao. Kiem tra lai nam hoac ket noi internet." -ForegroundColor Red
    exit 1
}

if ($MaxFiles -gt 0 -and $MaxFiles -lt $allFiles.Count) {
    Write-Host "   Gioi han: $MaxFiles files (test mode)" -ForegroundColor Yellow
    $allFiles = $allFiles | Select-Object -First $MaxFiles
}

# ═══════════════════════════════════════════════════════════════
# Bước 2: Tải từng file → import → xóa
# ═══════════════════════════════════════════════════════════════

Write-Host ""
Write-Host "🚀 Bat dau import..." -ForegroundColor Green
Write-Host ""

$startTime = Get-Date
$successCount = 0
$failCount = 0
$totalPapers = 0

for ($i = 0; $i -lt $allFiles.Count; $i++) {
    $file = $allFiles[$i]
    $fileName = Split-Path $file.S3Path -Leaf
    $localPath = Join-Path $TempDir $fileName

    $progress = "[{0}/{1}]" -f ($i + 1), $allFiles.Count
    $percent = [math]::Round(($i + 1) / $allFiles.Count * 100, 1)

    Write-Host "$progress $fileName ($($file.SizeMB) MB)" -ForegroundColor White

    try {
        # Download
        Write-Host "   ⬇ Downloading..." -ForegroundColor Gray -NoNewline
        aws s3 cp $file.S3Path $localPath --no-sign-request 2>&1 | Out-Null

        if (-not (Test-Path $localPath)) {
            throw "Download failed"
        }
        $dlSize = [math]::Round((Get-Item $localPath).Length / 1MB, 1)
        Write-Host " OK ($dlSize MB)" -ForegroundColor Gray

        # Import
        Write-Host "   📥 Importing..." -ForegroundColor Gray -NoNewline
        $importStart = Get-Date
        $output = python $ImporterScript --input $localPath 2>&1
        $importElapsed = [math]::Round(((Get-Date) - $importStart).TotalSeconds, 1)

        # Parse output de lay so papers
        if ($output -match 'Papers inserted:\s+([\d,]+)') {
            $papersImported = $matches[1] -replace ',', ''
            $totalPapers += [int]$papersImported
            Write-Host " OK ($importElapsed s, $papersImported papers)" -ForegroundColor Green
        } else {
            Write-Host " OK ($importElapsed s)" -ForegroundColor Green
        }

        $successCount++
    } catch {
        Write-Host " ❌ FAILED: $_" -ForegroundColor Red
        $failCount++
    } finally {
        # Luon xoa file de giai phong dung luong
        if (Test-Path $localPath) {
            Remove-Item $localPath -Force -ErrorAction SilentlyContinue
        }
    }

    # Progress update sau moi 10 files
    if (($i + 1) % 10 -eq 0) {
        $elapsed = [math]::Round(((Get-Date) - $startTime).TotalMinutes, 1)
        Write-Host ""
        Write-Host "   ── Progress: $percent% | $successCount OK / $failCount fail | Papers: $totalPapers | Time: $elapsed min ──" -ForegroundColor Cyan
        Write-Host ""
    }
}

# ═══════════════════════════════════════════════════════════════
# Bước 3: Tổng kết
# ═══════════════════════════════════════════════════════════════

$totalElapsed = [math]::Round(((Get-Date) - $startTime).TotalMinutes, 1)

Write-Host ""
Write-Host "╔══════════════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║  📊 COMPLETED                                       ║" -ForegroundColor Cyan
Write-Host "╠══════════════════════════════════════════════════════╣" -ForegroundColor Cyan
Write-Host ("║  Files processed:  {0,5}                             ║" -f $successCount) -ForegroundColor White
Write-Host ("║  Files failed:     {0,5}                             ║" -f $failCount) -ForegroundColor White
Write-Host ("║  Total papers:     {0,5}                             ║" -f $totalPapers) -ForegroundColor White
Write-Host ("║  Total time:       {0,5} min                         ║" -f $totalElapsed) -ForegroundColor White
Write-Host "╚══════════════════════════════════════════════════════╝" -ForegroundColor Cyan

# Don dep
if (Test-Path $TempDir) {
    Remove-Item $TempDir -Recurse -Force -ErrorAction SilentlyContinue
}

if ($totalPapers -gt 0) {
    Write-Host ""
    Write-Host "✅ Done! $totalPapers papers da duoc import." -ForegroundColor Green
    Write-Host ""
    Write-Host "🔗 Next: Rebuild Neo4j graph:" -ForegroundColor Yellow
    Write-Host "   curl -X POST http://localhost:8080/api/v1/admin/sync/reindex-keywords" -ForegroundColor Gray
}
