# Enciende SGE-JD completo con un solo clic: Docker, Oracle, Keycloak, backend y frontend.
# Uso: doble clic en iniciar_sgejd.bat (o: powershell -ExecutionPolicy Bypass -File iniciar_sgejd.ps1)
# Si algo ya está encendido, lo deja como está y sigue con lo siguiente.
param(
    [string]$Backend = (Resolve-Path "$PSScriptRoot\..").Path,
    [string]$Frontend = (Join-Path (Resolve-Path "$PSScriptRoot\..\..").Path 'bomerp-frontend'),
    [switch]$SinNavegador
)
$ErrorActionPreference = 'Continue'   # con 'Stop', PowerShell 5.1 corta al primer aviso que docker escribe en stderr
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$Host.UI.RawUI.WindowTitle = 'SGE-JD · Arranque'

function Paso([string]$t) { Write-Host ''; Write-Host "▶ $t" -ForegroundColor Cyan }
function Ok([string]$t) { Write-Host "  ✔ $t" -ForegroundColor Green }
function Falla([string]$t) { Write-Host "  ✖ $t" -ForegroundColor Red; Read-Host 'Pulse Enter para cerrar'; exit 1 }
function Escuchando([int]$puerto) { [bool](Get-NetTCPConnection -LocalPort $puerto -State Listen -ErrorAction SilentlyContinue) }
function Esperar([string]$que, [scriptblock]$listo, [int]$segundos) {
    $fin = (Get-Date).AddSeconds($segundos)
    while ((Get-Date) -lt $fin) {
        if (& $listo) { Ok "$que listo"; return }
        Write-Host '.' -NoNewline; Start-Sleep 3
    }
    Falla "$que no respondió en $segundos s"
}
function Http200([string]$url) {
    try { (Invoke-WebRequest -UseBasicParsing -Uri $url -TimeoutSec 5).StatusCode -eq 200 } catch { $false }
}

Write-Host '=== SGE-JD · JD Refrigeración S.A.C. ===' -ForegroundColor Yellow
if (-not (Test-Path "$Backend\pom.xml")) { Falla "No encuentro el backend en $Backend" }
if (-not (Test-Path "$Frontend\package.json")) { Falla "No encuentro el frontend en $Frontend (use -Frontend <ruta>)" }
# docker compose toma las contraseñas de .env (no se versiona): sin él no enciende Keycloak
if (-not (Test-Path "$Backend\.env")) { Falla "Falta $Backend\.env: copie .env.example como .env y ponga las contraseñas" }

# 1) Docker Desktop
Paso 'Docker Desktop'
docker info *> $null
if ($LASTEXITCODE -ne 0) {
    $exe = @("$env:LOCALAPPDATA\Programs\DockerDesktop\Docker Desktop.exe", "$env:ProgramFiles\Docker\Docker\Docker Desktop.exe") |
           Where-Object { Test-Path $_ } | Select-Object -First 1
    if (-not $exe) { Falla 'Docker Desktop no está instalado' }
    Start-Process $exe
    Esperar 'Docker' { docker info *> $null; $LASTEXITCODE -eq 0 } 180
} else { Ok 'Docker ya estaba encendido' }

# 2) Oracle (contenedor SGE-JD, puerto 1522)
Paso 'Base de datos Oracle'
docker start SGE-JD *> $null
if ($LASTEXITCODE -ne 0) { Falla 'No existe el contenedor SGE-JD (ver README: Guía de instalación)' }
# El contenedor no tiene healthcheck y su registro guarda los "READY" de arranques anteriores:
# solo cuenta el que aparezca desde este arranque.
$inicio = docker inspect -f '{{.State.StartedAt}}' SGE-JD
Esperar 'Oracle' { [bool]((docker logs --since $inicio SGE-JD 2>&1) -match 'DATABASE IS READY TO USE') } 240

# 3) Keycloak + su Postgres
Paso 'Keycloak (inicio de sesión)'
Push-Location $Backend
docker compose up -d keycloak-db keycloak *> $null
$codigo = $LASTEXITCODE
Pop-Location
if ($codigo -ne 0) { Falla 'docker compose no pudo encender Keycloak' }
Esperar 'Keycloak' { Http200 'http://localhost:38080/realms/bomerp/.well-known/openid-configuration' } 180

# 4) Backend Spring Boot (8080), en su propia ventana para ver el registro
Paso 'Backend (Spring Boot, puerto 8080)'
if (Escuchando 8080) { Ok 'El backend ya estaba encendido' }
else {
    Start-Process powershell -WorkingDirectory $Backend -ArgumentList '-NoExit', '-Command',
        "`$Host.UI.RawUI.WindowTitle='SGE-JD · Backend'; mvn spring-boot:run"
    Esperar 'Backend' { Http200 'http://localhost:8080/api/v1/publico/catalogo' } 300
}

# 5) Frontend Angular (4200), en su propia ventana
Paso 'Frontend (Angular, puerto 4200)'
if (Escuchando 4200) { Ok 'El frontend ya estaba encendido' }
else {
    Start-Process powershell -WorkingDirectory $Frontend -ArgumentList '-NoExit', '-Command',
        "`$Host.UI.RawUI.WindowTitle='SGE-JD · Frontend'; npm start"
    Esperar 'Frontend' { Http200 'http://localhost:4200' } 240
}

Write-Host ''
Write-Host '=== SGE-JD está listo: http://localhost:4200 ===' -ForegroundColor Yellow
Write-Host 'No cierre las ventanas "SGE-JD · Backend" ni "SGE-JD · Frontend" mientras use el sistema.'
Write-Host 'Para apagar todo: detener_sgejd.bat'
if (-not $SinNavegador) { Start-Process 'http://localhost:4200' }
