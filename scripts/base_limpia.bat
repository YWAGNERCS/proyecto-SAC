@echo off
REM Limpia la base de SGE-JD: ensayo, confirmacion, respaldo y limpieza.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0base_limpia.ps1" %*
pause
