@echo off
setlocal enabledelayedexpansion

echo ========================================
echo Dictionary Backup, Truncate and Convert
echo ========================================
echo.

python scripts/backup_truncate_and_convert.py --max_words 20000

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ERROR: Process failed!
    pause
    exit /b 1
)

echo.
echo ========================================
echo Process completed successfully!
echo ========================================
pause


