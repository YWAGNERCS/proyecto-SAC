@echo off
rem Deja la base de desarrollo como nueva con datos de demostracion (20 ventas por ano, 2024 a 2026).
rem Pide escribir BORRAR y saca un respaldo antes. Ver scripts\datos_demo.ps1.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0datos_demo.ps1" %*
pause
