@echo off
REM Respaldo de la base de datos de SGE-JD en C:\SGE-JD\respaldos.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0respaldar_sgejd.ps1" %*
pause
