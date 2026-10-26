@echo off
REM Enciende SGE-JD (Docker, Oracle, Keycloak, backend y frontend) y abre el navegador.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0iniciar_sgejd.ps1" %*
pause
