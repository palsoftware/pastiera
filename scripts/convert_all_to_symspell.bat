@echo off
setlocal enabledelayedexpansion

echo ========================================
echo Convert All Dictionaries to SymSpell
echo ========================================
echo.

python scripts/convert_all_to_symspell.py

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


