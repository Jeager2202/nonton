@echo off
chcp 65001 >nul
setlocal EnableExtensions
set "ROOT=%~dp0..\.."
for %%I in ("%ROOT%") do set "ROOT=%%~fI"
cd /d "%ROOT%"
title NONTON WEBAPP BUILDER - SMARTY FIXED

echo.
echo ============================================================
echo   NONTON WEBAPP BUILDER - SMARTY FIXED
echo   Terminal ini sengaja dibuat tetap terbuka.
echo ============================================================
echo Project: %ROOT%
echo.

call :find_python
if errorlevel 1 goto no_python

echo [1] Python command: %PY_CMD%
echo [2] Installing or verifying Python dependencies...
%PY_CMD% -m pip install flask requests PyGithub
if errorlevel 1 (
    echo.
    echo [WARNING] pip install gagal. Webapp tetap dicoba.
    echo Jika Flask belum ada, error Python akan tampil di terminal ini.
)

echo.
echo [3] Starting webapp server...
echo URL: http://127.0.0.1:5000
echo Jangan tutup terminal ini saat webapp dipakai.
echo.
%PY_CMD% -u "%ROOT%tools\nonton_webapp.py" %*
set "ERR=%ERRORLEVEL%"
echo.
echo Server berhenti dengan kode: %ERR%
echo Terminal tetap terbuka. Baca error di atas jika ada.
exit /b %ERR%

:find_python
set "PY_CMD="
py -3 --version >nul 2>&1
if %errorlevel% equ 0 set "PY_CMD=py -3"
if not defined PY_CMD (
    python --version >nul 2>&1
    if %errorlevel% equ 0 set "PY_CMD=python"
)
if not defined PY_CMD exit /b 1
exit /b 0

:no_python
echo [ERROR] Python tidak ditemukan.
echo Install Python 3.10 atau lebih baru dan centang Add Python to PATH.
echo Download: https://www.python.org/downloads/
exit /b 1
