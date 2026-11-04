@echo off
setlocal
rem Resolve the project from this file so the launcher can move with the checkout.
cd /d "%~dp0"
echo Starting SimRecrut...
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\dev\launch-local.ps1"
if errorlevel 1 (
    echo.
    echo Startup failed. Check the message above and runtime\local-logs.
) else (
    echo.
    echo SimRecrut is ready. Background services will keep running.
)
pause
endlocal
