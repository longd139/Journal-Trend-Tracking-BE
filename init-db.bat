@echo off
REM =============================================
REM SCITRACK — Database Init Script (Windows)
REM Chạy: init-db.bat
REM =============================================

echo.
echo ============================================
echo   SCITRACK — Database Initialization
echo ============================================
echo.

REM Doc password tu .env file (dong chua DATABASE_PASSWORD)
for /f "tokens=2 delims==" %%a in ('findstr "DATABASE_PASSWORD=" .env') do set SA_PASSWORD=%%a

if "%SA_PASSWORD%"=="" (
    echo [WARN] Khong tim thay DATABASE_PASSWORD trong .env, dung default
    set SA_PASSWORD=YourStrong!Passw0rd
)

echo [1/3] Waiting for SQL Server to be healthy...
:wait
docker exec scitrack-sqlserver /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P "%SA_PASSWORD%" -C -Q "SELECT 1" >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    timeout /t 2 >nul
    goto wait
)
echo [OK] SQL Server is ready.

echo [2/3] Creating database JournalTrendDB...
docker exec scitrack-sqlserver /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P "%SA_PASSWORD%" -C -Q "IF NOT EXISTS (SELECT * FROM sys.databases WHERE name='JournalTrendDB') CREATE DATABASE JournalTrendDB COLLATE Vietnamese_CI_AI"
echo [OK] Database created.

echo [3/3] Running schema script (journal_trend_db.sql)...
docker exec scitrack-sqlserver /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P "%SA_PASSWORD%" -C -d JournalTrendDB -i /init.sql
echo [OK] Schema initialized.

echo.
echo ============================================
echo   Database setup complete!
echo   Now run: mvn spring-boot:run
echo ============================================
