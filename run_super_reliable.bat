@echo off
cd /d "%~dp0"
cmd /k call "%~dp0tools\bat\run_webapp.cmd" --open-browser --port 5000
