@echo off
chcp 65001 >nul
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0"
title JEAGER22 JDK DOCTOR - SMARTY SARA

echo.
echo ============================================================
echo   JEAGER22 JDK DOCTOR - SMARTY SARA AUTO INSTALLER
echo   Alias: SARA // Mission: memastikan JDK 17 siap untuk NONTON
echo ============================================================
echo.

call :check_jdk17
if "%JDK17_OK%"=="1" (
    echo [OK] JDK 17 sudah terdeteksi.
    java -version
    javac -version
    echo.
    echo Tidak perlu install ulang.
    pause
    exit /b 0
)

echo [WARNING] JDK 17 belum siap.
echo JDK yang dibutuhkan project NONTON: JDK 17.
echo.
set "INSTALL_DIR=%USERPROFILE%\.jeager22\jdk-17"
set "DL_DIR=%TEMP%\jeager22-jdk-doctor"
set "ZIP_FILE=%DL_DIR%\temurin-jdk17.zip"
set "API_URL=https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse"

echo Target install portable:
echo %INSTALL_DIR%
echo.
echo Script ini akan download Temurin JDK 17, extract portable,
echo lalu set JAVA_HOME user environment ke folder tersebut.
echo.
choice /C YN /M "Lanjut download dan install JDK 17 portable?"
if errorlevel 2 (
    echo Dibatalkan user.
    pause
    exit /b 1
)

if exist "%DL_DIR%" rmdir /s /q "%DL_DIR%" >nul 2>&1
mkdir "%DL_DIR%" >nul 2>&1
if exist "%INSTALL_DIR%" rmdir /s /q "%INSTALL_DIR%" >nul 2>&1
mkdir "%INSTALL_DIR%" >nul 2>&1

echo.
echo [1] Downloading Temurin JDK 17...
powershell -NoProfile -ExecutionPolicy Bypass -Command "[Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri '%API_URL%' -OutFile '%ZIP_FILE%' -UseBasicParsing"
if errorlevel 1 (
    echo [ERROR] Download JDK gagal. Cek internet / antivirus / firewall.
    pause
    exit /b 1
)

echo.
echo [2] Extracting JDK...
powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Path '%ZIP_FILE%' -DestinationPath '%DL_DIR%\extract' -Force"
if errorlevel 1 (
    echo [ERROR] Extract JDK gagal.
    pause
    exit /b 1
)

set "FOUND_JDK="
for /d %%d in ("%DL_DIR%\extract\jdk-17*") do set "FOUND_JDK=%%~fd"
if not defined FOUND_JDK (
    for /d %%d in ("%DL_DIR%\extract\*") do if exist "%%~fd\bin\javac.exe" set "FOUND_JDK=%%~fd"
)
if not defined FOUND_JDK (
    echo [ERROR] Folder JDK hasil extract tidak ditemukan.
    pause
    exit /b 1
)

xcopy "%FOUND_JDK%\*" "%INSTALL_DIR%\" /E /I /Y >nul
if not exist "%INSTALL_DIR%\bin\javac.exe" (
    echo [ERROR] javac.exe tidak ditemukan setelah copy.
    pause
    exit /b 1
)

echo.
echo [3] Setting JAVA_HOME and PATH untuk user...
setx JAVA_HOME "%INSTALL_DIR%" >nul
powershell -NoProfile -ExecutionPolicy Bypass -Command "$jdk='%INSTALL_DIR%\bin'; $old=[Environment]::GetEnvironmentVariable('Path','User'); if([string]::IsNullOrWhiteSpace($old)){ $new=$jdk } elseif($old -notlike ('*'+$jdk+'*')){ $new=$jdk+';'+$old } else { $new=$old }; [Environment]::SetEnvironmentVariable('Path',$new,'User')"

set "JAVA_HOME=%INSTALL_DIR%"
set "PATH=%INSTALL_DIR%\bin;%PATH%"

echo.
echo [4] Verifying JDK...
java -version
javac -version
call :check_jdk17
if not "%JDK17_OK%"=="1" (
    echo.
    echo [WARNING] Install selesai, tapi terminal ini belum mendeteksi JDK 17.
    echo Tutup semua CMD, buka lagi, lalu jalankan RUN_DIAGNOSTIC.bat.
    pause
    exit /b 1
)

echo.
echo ✅ JDK 17 siap. Jeager22 JDK Doctor selesai.
echo Sekarang jalankan: RUN_NOW.bat atau build_apk_now.bat
echo.
pause
exit /b 0

:check_jdk17
set "JDK17_OK=0"
javac -version >nul 2>&1
if errorlevel 1 exit /b 0
for /f "tokens=2 delims= " %%v in ('javac -version 2^>^&1') do set "JDK_VER=%%v"
for /f "tokens=1 delims=." %%m in ("!JDK_VER!") do set "JDK_MAJOR=%%m"
if not defined JDK_MAJOR set "JDK_MAJOR=0"
if !JDK_MAJOR! GEQ 17 set "JDK17_OK=1"
exit /b 0
