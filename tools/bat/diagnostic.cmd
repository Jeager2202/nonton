@echo off
chcp 65001 >nul
setlocal EnableExtensions
set "ROOT=%~dp0..\.."
for %%I in ("%ROOT%") do set "ROOT=%%~fI"
cd /d "%ROOT%"
title NONTON DIAGNOSTIC - SMARTY FIXED

echo.
echo ============================================================
echo   NONTON DIAGNOSTIC - SMARTY FIXED
echo ============================================================
echo Project: %ROOT%
echo.

echo [PYTHON]
py -3 --version 2>nul
python --version 2>nul
echo.

echo [JAVA]
java -version
echo.

echo [JAVAC]
javac -version
echo.

echo [ANDROID SDK ENV]
echo ANDROID_HOME=%ANDROID_HOME%
echo ANDROID_SDK_ROOT=%ANDROID_SDK_ROOT%
echo.

echo [PROJECT FILES]
if exist "gradle\wrapper\gradle-wrapper.jar" (echo OK  gradle-wrapper.jar) else (echo BAD gradle-wrapper.jar missing)
if exist "gradle\wrapper\gradle-wrapper.properties" (echo OK  gradle-wrapper.properties) else (echo BAD gradle-wrapper.properties missing)
if exist "settings.gradle.kts" (echo OK  settings.gradle.kts) else if exist "settings.gradle" (echo OK settings.gradle) else (echo BAD settings file missing)
if exist "build.gradle.kts" (echo OK  build.gradle.kts) else if exist "build.gradle" (echo OK build.gradle) else (echo BAD root build file missing)
if exist "app\build.gradle.kts" (echo OK  app\build.gradle.kts) else if exist "app\build.gradle" (echo OK app\build.gradle) else (echo BAD app build file missing)
if exist "app\src\main\AndroidManifest.xml" (echo OK  AndroidManifest.xml) else (echo BAD AndroidManifest.xml missing)
if exist "app\src\main\java\com\jeager22\nonton\MainActivity.kt" (echo OK  MainActivity.kt) else (echo BAD MainActivity.kt missing)
echo.

echo Jika semua OK, jalankan RUN_NOW.bat atau build_apk_now.bat.
echo Jika ada BAD, kirim screenshot terminal ini ke Smarty.
echo.
exit /b 0
