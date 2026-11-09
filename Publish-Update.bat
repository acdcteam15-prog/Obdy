@echo off
setlocal
title Obdy - publish an update on GitHub
cd /d "%~dp0"
rem Double-click. Builds the signed APK on GitHub and publishes it as a Release; the app then offers it under Settings - Updates.
rem Optional: Publish-Update.bat what is new in this version
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0setup\Build-Obdy.ps1" -Release -Message "%*"
echo.
pause
