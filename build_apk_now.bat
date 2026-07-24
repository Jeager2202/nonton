@echo off
cd /d "%~dp0"
cmd /k call "%~dp0tools\bat\build_apk.cmd"
