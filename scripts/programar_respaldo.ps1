# Crea (o actualiza) la tarea programada de Windows "SGE-JD respaldo diario", que ejecuta
# respaldo_automatico.ps1 todos los días a la hora indicada. Si la PC estaba apagada a esa hora,
# Windows la ejecuta apenas se encienda. Corre con el usuario actual (necesita Docker Desktop abierto).
#
#   programar_respaldo.bat                                   → 18:30, sin copia fuera de la PC
#   programar_respaldo.bat -Destino "D:\RespaldosSGEJD"      → además copia ahí (USB, OneDrive, red...)
#   programar_respaldo.bat -Hora 13:00 -Conservar 30
#   programar_respaldo.bat -Quitar                           → elimina la tarea
param(
    [string]$Hora = '18:30',
    [string]$Destino = '',
    [int]$Conservar = 14,
    [switch]$Quitar
)
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$nombre = 'SGE-JD respaldo diario'

if ($Quitar) {
    Unregister-ScheduledTask -TaskName $nombre -Confirm:$false -ErrorAction SilentlyContinue
    Write-Host "✔ Tarea '$nombre' eliminada." -ForegroundColor Green
    exit 0
}
if ($Destino) {
    New-Item -ItemType Directory -Force $Destino | Out-Null
}

$script = Join-Path $PSScriptRoot 'respaldo_automatico.ps1'
$argumentos = "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File `"$script`" -Conservar $Conservar"
if ($Destino) { $argumentos += " -Destino `"$Destino`"" }

$accion = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument $argumentos
$cuando = New-ScheduledTaskTrigger -Daily -At $Hora
# StartWhenAvailable: si la PC estaba apagada a la hora, corre al encenderla
$ajustes = New-ScheduledTaskSettingsSet -StartWhenAvailable -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries `
    -ExecutionTimeLimit (New-TimeSpan -Hours 1)
Register-ScheduledTask -TaskName $nombre -Action $accion -Trigger $cuando -Settings $ajustes `
    -Description 'Respaldo diario de la base de SGE-JD (Oracle Data Pump) y copia fuera de la PC. Registro en C:\SGE-JD\respaldos\respaldo_automatico.log' `
    -Force | Out-Null

Write-Host "✔ Tarea '$nombre' programada todos los días a las $Hora." -ForegroundColor Green
if ($Destino) { Write-Host "  Copia fuera de la PC en: $Destino" } else {
    Write-Host '  ⚠ Sin -Destino: el respaldo queda solo en esta PC. Indique una carpeta de USB, OneDrive o red.' -ForegroundColor Yellow
}
Write-Host "  Se conservan los últimos $Conservar respaldos. Registro: C:\SGE-JD\respaldos\respaldo_automatico.log"
