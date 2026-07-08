# run-import.ps1 — Import papers from OpenAlex S3
$python = "C:\Users\ADMIN\AppData\Local\Programs\Python\Python314\python.exe"

Set-Location "D:\FPT\KI_5\SWP\SRC\Journal-Trend-Tracking-BE\tools\openalex-snapshot-importer"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$env:PYTHONUTF8 = "1"
$env:PYTHONIOENCODING = "utf-8"

if ($args.Count -eq 0) {
    Write-Host "Usage: .\run-import.ps1 --year-from 2026 --max-files 5"
    Write-Host ""
    Write-Host "Examples:"
    Write-Host "  .\run-import.ps1 --year-from 2026 --scan-only"
    Write-Host "  .\run-import.ps1 --year-from 2026 --max-files 5"
    Write-Host "  .\run-import.ps1 --year-from 2026 --max-files 100"
    exit
}

& $python auto_import.py @args
