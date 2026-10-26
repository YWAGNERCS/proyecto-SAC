@echo off
REM Apaga SGE-JD. Los datos quedan guardados en Oracle.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0detener_sgejd.ps1" %*
pause
