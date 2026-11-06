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

# Se buscan los procesos por su línea de comando y no por el puerto: en algunas PC (como la de
# desarrollo) netstat/Get-NetTCPConnection no muestran 8080 y 4200 como "en escucha" aunque estén
# atendiendo, y el apagado decía "ya estaba apagado" sin apagar nada.
$todos = @(Get-CimInstance Win32_Process)

function Descendientes([int]$id) {
    foreach ($h in $todos | Where-Object { $_.ParentProcessId -eq $id }) {
        Descendientes $h.ProcessId
        $h
    }
}

function Apagar([string]$nombre, [scriptblock]$filtro) {
    $raices = @($todos | Where-Object $filtro)
    if (-not $raices) { Write-Host "  - $nombre ya estaba apagado"; return }
    # Primero los hijos (java de la app, ng serve) y al final la ventana o el proceso que los lanzó
    $lista = @(foreach ($r in $raices) { Descendientes $r.ProcessId; $r }) | Sort-Object ProcessId -Unique
    Write-Host "  $verbo $nombre ($($lista.Count) procesos: $(($lista.Name | Sort-Object -Unique) -join ', '))" -ForegroundColor Cyan
    if (-not $Simular) {
        foreach ($x in $lista) { Stop-Process -Id $x.ProcessId -Force -ErrorAction SilentlyContinue }
    }
}

Write-Host '=== Apagando SGE-JD ===' -ForegroundColor Yellow
# Ventanas que abre iniciar_sgejd.bat, o el proceso suelto si se arrancó a mano
Apagar 'Frontend' { ($_.Name -eq 'powershell.exe' -and $_.CommandLine -match "SGE-JD · Frontend") -or
                    ($_.Name -eq 'node.exe' -and $_.CommandLine -match 'bomerp-frontend.*ng\.js.*serve') }
Apagar 'Backend'  { ($_.Name -eq 'powershell.exe' -and $_.CommandLine -match "SGE-JD · Backend") -or
                    ($_.Name -eq 'java.exe' -and $_.CommandLine -match 'bomerp-backend') }
if (-not $DejarBaseDatos) {
    Write-Host "  $verbo Keycloak y Oracle (los datos se conservan)" -ForegroundColor Cyan
    if (-not $Simular) {
        Push-Location $Backend; docker compose stop keycloak keycloak-db *> $null; Pop-Location
        docker stop SGE-JD *> $null
    }
}
Write-Host ''
Write-Host $(if ($Simular) { 'Simulación: no se apagó nada.' } else { 'SGE-JD apagado. Para encenderlo: iniciar_sgejd.bat' }) -ForegroundColor Yellow
