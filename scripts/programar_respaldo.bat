@echo off
REM Programa el respaldo diario automatico de SGE-JD (tarea de Windows). Ver programar_respaldo.ps1.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0programar_respaldo.ps1" %*
pause
