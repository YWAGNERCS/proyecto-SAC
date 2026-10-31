# Respaldo automático de SGE-JD: lo ejecuta todos los días la tarea programada "SGE-JD respaldo diario"
# (se crea con programar_respaldo.bat). También se puede ejecutar a mano.
#
#  1. Saca el respaldo con respaldar_sgejd.ps1 (Oracle Data Pump) en C:\SGE-JD\respaldos.
#  2. Copia el .dmp a la carpeta -Destino (fuera de la PC: OneDrive, Google Drive, un USB o una
#     carpeta de red). Si la carpeta no está disponible (USB desconectado), lo anota y sigue.
#  3. Borra los respaldos más antiguos y deja los últimos -Conservar en cada lugar.
# Todo queda anotado en respaldos\respaldo_automatico.log. Si Oracle está apagado no lo enciende:
# no hay nada nuevo que respaldar y lo anota como aviso.
param(
    [string]$Destino = '',
    [int]$Conservar = 14
)
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$carpeta = Join-Path (Resolve-Path "$PSScriptRoot\..\..").Path 'respaldos'
New-Item -ItemType Directory -Force $carpeta | Out-Null
$log = Join-Path $carpeta 'respaldo_automatico.log'

function Anotar([string]$texto) {
    $linea = "$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')  $texto"
    Add-Content -Path $log -Value $linea -Encoding UTF8
    Write-Host $linea
}

# Deja solo los $Conservar respaldos más recientes (.dmp y su .log) de una carpeta
function Rotar([string]$dir) {
    Get-ChildItem $dir -Filter 'sgejd_*.dmp' | Sort-Object LastWriteTime -Descending | Select-Object -Skip $Conservar |
        ForEach-Object {
            Remove-Item $_.FullName -Force
            Remove-Item ($_.FullName -replace '\.dmp$', '.log') -Force -ErrorAction SilentlyContinue
            Anotar "Borrado por antiguo: $($_.FullName)"
        }
}

try {
    if ((docker inspect -f '{{.State.Running}}' SGE-JD 2>$null) -ne 'true') {
        Anotar 'AVISO: Oracle (SGE-JD) está apagado; hoy no se respaldó. Si el sistema se usó, encienda y respalde a mano.'
        exit 0
    }
    $nombre = 'sgejd_' + (Get-Date -Format 'yyyyMMdd_HHmm')
    & (Join-Path $PSScriptRoot 'respaldar_sgejd.ps1') -Carpeta $carpeta -Nombre $nombre | Out-Null
    $dmp = Join-Path $carpeta "$nombre.dmp"
    if (-not (Test-Path $dmp)) { throw "respaldar_sgejd.ps1 no dejó $dmp" }
    Anotar "Respaldo listo: $dmp ($([int]((Get-Item $dmp).Length / 1KB)) KB)"
    Rotar $carpeta

    if ($Destino) {
        if (Test-Path $Destino) {
            Copy-Item $dmp $Destino -Force
            Copy-Item ($dmp -replace '\.dmp$', '.log') $Destino -Force -ErrorAction SilentlyContinue
            Anotar "Copia fuera de la PC: $(Join-Path $Destino "$nombre.dmp")"
            Rotar $Destino
        } else {
            Anotar "AVISO: la carpeta de copia $Destino no está disponible (¿USB desconectado?). El respaldo quedó solo en la PC."
        }
    }
} catch {
    Anotar "ERROR: $($_.Exception.Message)"
    exit 1
}
