# Respaldo automático de SGE-JD: lo ejecuta todos los días la tarea programada "SGE-JD respaldo diario"
# (se crea con programar_respaldo.bat). También se puede ejecutar a mano.
#
#  1. Saca el respaldo con respaldar_sgejd.ps1 (Oracle Data Pump) en C:\SGE-JD\respaldos.
#  2. Copia el .dmp a la carpeta -Destino (fuera de la PC: OneDrive, Google Drive, un USB o una
#     carpeta de red). Si la carpeta no está disponible (USB desconectado), lo anota y sigue.
#  3. Borra los respaldos más antiguos y deja los últimos -Conservar en cada lugar.
# Todo queda anotado en respaldos\respaldo_automatico.log. Si Oracle está apagado no lo enciende:
# no hay nada nuevo que respaldar y lo anota como aviso.
#
# Qué base respalda (-Contenedor): por defecto la que esté encendida, primero PRODUCCIÓN (sge-jd-oracle-prod)
# y si no, desarrollo (SGE-JD). Los archivos llevan el entorno en el nombre (sgejd_diario_prod_..., _dev_...) y
# la rotación solo toca esos: los respaldos manuales (sgejd_antes_de_..., etc.) nunca se borran solos.
param(
    [string]$Destino = '',
    [int]$Conservar = 14,
    [ValidateSet('auto', 'sge-jd-oracle-prod', 'SGE-JD')][string]$Contenedor = 'auto'
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

# Deja solo los $Conservar respaldos diarios más recientes (.dmp y su .log) de ese entorno en una carpeta
function Rotar([string]$dir, [string]$prefijo) {
    Get-ChildItem $dir -Filter "$prefijo*.dmp" | Sort-Object LastWriteTime -Descending | Select-Object -Skip $Conservar |
        ForEach-Object {
            Remove-Item $_.FullName -Force
            Remove-Item ($_.FullName -replace '\.dmp$', '.log') -Force -ErrorAction SilentlyContinue
            Anotar "Borrado por antiguo: $($_.FullName)"
        }
}

try {
    $encendidos = @('sge-jd-oracle-prod', 'SGE-JD') | Where-Object {
        ($Contenedor -eq 'auto' -or $Contenedor -eq $_) -and (docker inspect -f '{{.State.Running}}' $_ 2>$null) -eq 'true' }
    if (-not $encendidos) {
        $cual = if ($Contenedor -eq 'auto') { 'ni producción (sge-jd-oracle-prod) ni desarrollo (SGE-JD)' } else { $Contenedor }
        Anotar "AVISO: Oracle está apagado ($cual); hoy no se respaldó. Si el sistema se usó, encienda y respalde a mano."
        exit 0
    }
    $base = @($encendidos)[0]
    $entorno = if ($base -eq 'sge-jd-oracle-prod') { 'prod' } else { 'dev' }
    $prefijo = "sgejd_diario_${entorno}_"
    $nombre = $prefijo + (Get-Date -Format 'yyyyMMdd_HHmm')
    # En un proceso aparte: con 'Stop' heredado, el primer aviso que docker escribe en stderr cortaba el respaldo
    # (PowerShell 5.1 lo toma como error) y el diario fallaba siempre con un mensaje vacío (corregido el 11/11/2026)
    powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'respaldar_sgejd.ps1') `
        -Carpeta $carpeta -Nombre $nombre -Contenedor $base | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "respaldar_sgejd.ps1 terminó con código $LASTEXITCODE (Oracle: $base)" }
    $dmp = Join-Path $carpeta "$nombre.dmp"
    if (-not (Test-Path $dmp)) { throw "respaldar_sgejd.ps1 no dejó $dmp" }
    Anotar "Respaldo listo ($base): $dmp ($([int]((Get-Item $dmp).Length / 1KB)) KB)"
    Rotar $carpeta $prefijo

    if ($Destino) {
        if (Test-Path $Destino) {
            Copy-Item $dmp $Destino -Force
            Copy-Item ($dmp -replace '\.dmp$', '.log') $Destino -Force -ErrorAction SilentlyContinue
            Anotar "Copia fuera de la PC: $(Join-Path $Destino "$nombre.dmp")"
            Rotar $Destino $prefijo
        } else {
            Anotar "AVISO: la carpeta de copia $Destino no está disponible (¿USB desconectado?). El respaldo quedó solo en la PC."
        }
    }
} catch {
    Anotar "ERROR: $($_.Exception.Message)"
    exit 1
}
