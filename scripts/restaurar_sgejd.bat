@echo off
REM Vuelve la base de SGE-JD al estado de un respaldo. Uso: restaurar_sgejd.bat <archivo.dmp>
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0restaurar_sgejd.ps1" %*
pause
