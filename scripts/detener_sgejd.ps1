# Apaga SGE-JD: frontend (4200), backend (8080), Keycloak y Oracle. Los datos quedan guardados.
# Uso: doble clic en detener_sgejd.bat. Con -Simular solo muestra lo que apagaría.
param(
    [string]$Backend = (Resolve-Path "$PSScriptRoot\..").Path,
    [switch]$Simular,
    [switch]$DejarBaseDatos   # deja Oracle y Keycloak encendidos
)
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$Host.UI.RawUI.WindowTitle = 'SGE-JD · Apagado'
$verbo = if ($Simular) { 'Apagaría' } else { 'Apagando' }

function ApagarPuerto([int]$puerto, [string]$nombre) {
    $c = Get-NetTCPConnection -LocalPort $puerto -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $c) { Write-Host "  - $nombre ya estaba apagado"; return }
    $p = Get-Process -Id $c.OwningProcess
    Write-Host "  $verbo $nombre ($($p.ProcessName), PID $($p.Id))" -ForegroundColor Cyan
    if (-not $Simular) { Stop-Process -Id $p.Id -Force }
}

Write-Host '=== Apagando SGE-JD ===' -ForegroundColor Yellow
ApagarPuerto 4200 'Frontend'
ApagarPuerto 8080 'Backend'
if (-not $DejarBaseDatos) {
    Write-Host "  $verbo Keycloak y Oracle (los datos se conservan)" -ForegroundColor Cyan
    if (-not $Simular) {
        Push-Location $Backend; docker compose stop keycloak keycloak-db *> $null; Pop-Location
        docker stop SGE-JD *> $null
    }
}
Write-Host ''
Write-Host $(if ($Simular) { 'Simulación: no se apagó nada.' } else { 'SGE-JD apagado. Para encenderlo: iniciar_sgejd.bat' }) -ForegroundColor Yellow
