@echo off
chcp 65001 >nul
setlocal EnableExtensions EnableDelayedExpansion
set "ROOT=%~dp0..\.."
for %%I in ("%ROOT%") do set "ROOT=%%~fI"
cd /d "%ROOT%"
title NONTON BUILD APK - SMARTY FIXED

set "ANDROID_API=34"
set "BUILD_TOOLS=34.0.0"

echo.
echo ============================================================
echo   NONTON BUILD APK - SMARTY FIXED SOURCE ASLI
echo   Terminal ini sengaja dibuat tetap terbuka.
echo ============================================================
echo Project: %ROOT%
echo.

echo [1] Checking Java and JDK 17...
java -version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Java tidak ditemukan.
    echo Install JDK 17 lalu masukkan folder bin ke PATH.
    echo Download: https://adoptium.net/
    exit /b 1
)
javac -version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] javac tidak ditemukan. Yang terinstall kemungkinan JRE, bukan JDK.
    echo Install JDK 17 lalu masukkan folder bin ke PATH.
    echo Download: https://adoptium.net/
    exit /b 1
)
for /f "tokens=2 delims= " %%v in ('javac -version 2^>^&1') do set "JDK_VER=%%v"
for /f "tokens=1 delims=." %%m in ("!JDK_VER!") do set "JDK_MAJOR=%%m"
if not defined JDK_MAJOR set "JDK_MAJOR=0"
if !JDK_MAJOR! LSS 17 (
    echo [ERROR] Project NONTON asli butuh JDK 17.
    echo Terdeteksi:
    java -version
    javac -version
    echo Install JDK 17: https://adoptium.net/
    exit /b 1
)
java -version
javac -version
echo Java and JDK OK.

echo.
echo [2] Checking project completeness...
if not exist "%ROOT%\gradle\wrapper\gradle-wrapper.jar" goto missing_wrapper
if not exist "%ROOT%\gradle\wrapper\gradle-wrapper.properties" goto missing_wrapper
if not exist "%ROOT%\gradlew.bat" goto missing_wrapper
if not exist "%ROOT%\settings.gradle.kts" if not exist "%ROOT%\settings.gradle" goto missing_project
if not exist "%ROOT%\build.gradle.kts" if not exist "%ROOT%\build.gradle" goto missing_project
if not exist "%ROOT%\app\build.gradle.kts" if not exist "%ROOT%\app\build.gradle" goto missing_project
if not exist "%ROOT%\app\src\main\AndroidManifest.xml" goto missing_project
if not exist "%ROOT%\app\src\main\java\com\jeager22\nonton\MainActivity.kt" if not exist "%ROOT%\app\src\main\java\com\jeager22\nonton\MainActivity.java" goto missing_project
echo Project files OK.

echo.
echo [3] Detecting Android SDK...
set "SDK="
if defined ANDROID_HOME if exist "%ANDROID_HOME%" set "SDK=%ANDROID_HOME%"
if not defined SDK if defined ANDROID_SDK_ROOT if exist "%ANDROID_SDK_ROOT%" set "SDK=%ANDROID_SDK_ROOT%"
if not defined SDK if exist "%LOCALAPPDATA%\Android\Sdk" set "SDK=%LOCALAPPDATA%\Android\Sdk"
if not defined SDK if exist "%USERPROFILE%\AppData\Local\Android\Sdk" set "SDK=%USERPROFILE%\AppData\Local\Android\Sdk"
if not defined SDK if exist "C:\Android\Sdk" set "SDK=C:\Android\Sdk"
if not defined SDK set "SDK=%USERPROFILE%\android-sdk"
echo SDK target: %SDK%
set "ANDROID_HOME=%SDK%"
set "ANDROID_SDK_ROOT=%SDK%"
if not exist "%SDK%" mkdir "%SDK%" >nul 2>&1

echo.
echo [4] Preparing Android command-line tools...
set "SDKMGR=%SDK%\cmdline-tools\latest\bin\sdkmanager.bat"
if not exist "%SDKMGR%" (
    echo sdkmanager belum ada. Download command-line tools...
    set "CMDZIP=%SDK%\cmdline-tools.zip"
    set "TMP=%SDK%\cmdline-tools-tmp"
    powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -Uri 'https://dl.google.com/android/repository/commandlinetools-win-9477386_latest.zip' -OutFile '%CMDZIP%' -UseBasicParsing"
    if errorlevel 1 (
        echo [ERROR] Gagal download Android command-line tools.
        exit /b 1
    )
    if exist "%TMP%" rmdir /s /q "%TMP%"
    mkdir "%TMP%" >nul 2>&1
    powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Path '%CMDZIP%' -DestinationPath '%TMP%' -Force"
    del "%CMDZIP%" >nul 2>&1
    if exist "%SDK%\cmdline-tools\latest" rmdir /s /q "%SDK%\cmdline-tools\latest"
    if not exist "%SDK%\cmdline-tools" mkdir "%SDK%\cmdline-tools"
    if exist "%TMP%\cmdline-tools" (
        move "%TMP%\cmdline-tools" "%SDK%\cmdline-tools\latest" >nul
    ) else (
        mkdir "%SDK%\cmdline-tools\latest" >nul 2>&1
        xcopy "%TMP%\*" "%SDK%\cmdline-tools\latest\" /E /I /Y >nul
    )
    rmdir /s /q "%TMP%" >nul 2>&1
)
if not exist "%SDKMGR%" (
    echo [ERROR] sdkmanager masih tidak ditemukan: %SDKMGR%
    exit /b 1
)
echo sdkmanager OK: %SDKMGR%

echo.
echo [5] Accepting licenses and installing SDK packages...
(for /L %%i in (1,1,80) do @echo y) | call "%SDKMGR%" --sdk_root="%SDK%" --licenses
call "%SDKMGR%" --sdk_root="%SDK%" "platform-tools" "platforms;android-%ANDROID_API%" "build-tools;%BUILD_TOOLS%"
if errorlevel 1 (
    echo [WARNING] sdkmanager package install mengembalikan error. Build tetap dicoba jika package sudah ada.
)
if not exist "%SDK%\platforms\android-%ANDROID_API%\android.jar" (
    echo [ERROR] Android platform android-%ANDROID_API% belum terinstall.
    exit /b 1
)
if not exist "%SDK%\build-tools\%BUILD_TOOLS%" (
    echo [ERROR] Build-tools %BUILD_TOOLS% belum terinstall.
    exit /b 1
)

echo.
echo [6] Writing local.properties and low-RAM Gradle environment...
set "SDK_FWD=%SDK:\=/%"
> "%ROOT%\local.properties" echo sdk.dir=%SDK_FWD%
type "%ROOT%\local.properties"
if not exist "%ROOT%\.tmp" mkdir "%ROOT%\.tmp" >nul 2>&1
set "GRADLE_USER_HOME=%USERPROFILE%\.gradle-nonton"
set "GRADLE_OPTS=-Djava.io.tmpdir=%ROOT%\.tmp"

echo.
echo [7] Building APK with Gradle Wrapper...
call "%ROOT%\gradlew.bat" clean assembleDebug --stacktrace --no-daemon -Dorg.gradle.jvmargs=-Xmx768m
set "BUILD_RESULT=%errorlevel%"
if not "%BUILD_RESULT%"=="0" (
    echo.
    echo [ERROR] BUILD FAILED. Code: %BUILD_RESULT%
    echo Baca log di atas. Terminal ini tetap terbuka.
    exit /b %BUILD_RESULT%
)

echo.
echo [8] Finding APK...
set "APK="
for /r "%ROOT%\app\build\outputs\apk\debug" %%f in (*.apk) do set "APK=%%f"
if not defined APK (
    echo [ERROR] Build sukses tapi APK tidak ditemukan.
    exit /b 1
)
copy /Y "!APK!" "%ROOT%\nonton.apk" >nul
echo.
echo BUILD SUCCESS.
echo APK asli : !APK!
echo APK copy : %ROOT%\nonton.apk
explorer "%ROOT%"
exit /b 0

:missing_wrapper
echo [ERROR] Gradle Wrapper tidak lengkap.
echo Pastikan ada gradlew.bat dan gradle\wrapper\gradle-wrapper.jar.
exit /b 1

:missing_project
echo [ERROR] Struktur project Android/Kotlin tidak lengkap.
echo Pastikan ada settings.gradle.kts, build.gradle.kts, app\build.gradle.kts, AndroidManifest.xml, MainActivity.kt.
exit /b 1
